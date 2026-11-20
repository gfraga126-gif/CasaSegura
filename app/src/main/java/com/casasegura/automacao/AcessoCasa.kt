package com.casasegura.automacao

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID

internal enum class PerfilConta(val codigo: String, val titulo: String) {
    ASSISTIDA("assistida", "Pessoa assistida"),
    TUTOR("tutor", "Tutor")
}

internal data class SessaoCasa(val servidor: String, val usuario: String, val perfil: PerfilConta, val token: String)
internal data class VinculoCasa(val id: String = "", val ativo: Boolean = false, val codigo: String = "")

internal class AcessoCasa private constructor(private val contexto: Context) {
    private val preferencias = contexto.getSharedPreferences("acesso_casa", Context.MODE_PRIVATE)
    private val rede = Mutex()
    val sessao = MutableStateFlow(carregarSessao())
    val vinculo = MutableStateFlow(
        VinculoCasa(preferencias.getString("vinculo", "").orEmpty(), preferencias.getBoolean("vinculado", false))
    )
    val recebidos = MutableStateFlow(carregarRecebidos())

    private fun carregarSessao(): SessaoCasa? {
        val token = preferencias.getString("token", null) ?: return null
        return SessaoCasa(
            preferencias.getString("servidor", SERVIDOR_EMULADOR).orEmpty(),
            preferencias.getString("usuario", "").orEmpty(),
            PerfilConta.entries.firstOrNull { it.codigo == preferencias.getString("perfil", "") } ?: PerfilConta.ASSISTIDA,
            token
        )
    }

    suspend fun autenticar(servidor: String, usuario: String, senha: String, cadastro: PerfilConta?) =
        withContext(Dispatchers.IO) {
            rede.withLock {
                val base = validarServidor(servidor)
                val dados = JSONObject().put("usuario", usuario.trim()).put("senha", senha)
                cadastro?.let { dados.put("perfil", it.codigo) }
                val resultado = requisicao(base, if (cadastro == null) "/api/sessoes" else "/api/contas", "POST", dados)
                val perfil = PerfilConta.entries.firstOrNull { it.codigo == resultado.optString("perfil") }
                    ?: throw IOException("O servidor enviou um perfil inválido.")
                val nova = SessaoCasa(base, resultado.getString("usuario"), perfil, resultado.getString("token"))
                preferencias.edit().putString("token", nova.token).putString("usuario", nova.usuario)
                    .putString("perfil", nova.perfil.codigo).putString("servidor", base).apply()
                sessao.value = nova
                agendarSincronizacao()
            }
        }

    suspend fun sair() = withContext(Dispatchers.IO) {
        rede.withLock {
            sessao.value?.let {
                try {
                    requisicao(it.servidor, "/api/sessoes", "DELETE", token = it.token)
                } catch (_: IOException) {
                    // É possível encerrar a sessão local mesmo sem internet.
                }
            }
            limparSessao()
        }
    }

    suspend fun gerarCodigo() = alterarVinculo("/api/vinculo/codigo", "POST")

    suspend fun vincular(codigo: String) =
        alterarVinculo("/api/vinculo/entrar", "POST", JSONObject().put("codigo", codigo.trim()))

    suspend fun desvincular() = alterarVinculo("/api/vinculo", "DELETE")

    private suspend fun alterarVinculo(rota: String, metodo: String, dados: JSONObject? = null) =
        withContext(Dispatchers.IO) {
            rede.withLock {
                val atual = sessao.value ?: throw IOException("Entre para continuar.")
                val resultado = requisicao(atual.servidor, rota, metodo, dados, atual.token)
                atualizarVinculo(resultado)
            }
        }

    fun registrar(descricao: String): Boolean {
        synchronized(this) {
            val atual = vinculo.value
            if (sessao.value == null || !atual.ativo) return false
            val registros = fila()
            registros.put(JSONObject().put("chave", UUID.randomUUID().toString())
                .put("descricao", descricao.take(240)).put("vinculo", atual.id))
            salvarFila(registros)
            return true
        }
    }

    suspend fun sincronizar(): Boolean = withContext(Dispatchers.IO) {
        rede.withLock {
            val atual = sessao.value ?: return@withLock true
            try {
                consultarAtividades(atual)
                if (!vinculo.value.ativo) return@withLock true
                repetirEnvio(atual)
                true
            } catch (erro: RespostaServidor) {
                if (erro.status == 401) {
                    limparSessao()
                    true
                } else {
                    false
                }
            } catch (_: IOException) {
                false
            }
        }
    }

    private fun consultarAtividades(atual: SessaoCasa) {
        val anterior = vinculo.value
        val cursor = preferencias.getLong("cursor", 0)
        val resultado = requisicao(
            atual.servidor,
            "/api/atividades?vinculo=${anterior.id}&depois=$cursor",
            token = atual.token
        )
        atualizarVinculo(resultado)
        if (!vinculo.value.ativo) return

        val novos = resultado.getJSONArray("eventos")
        var ultimo = preferencias.getLong("cursor", 0)
        val registros = recebidos.value.toMutableList()
        for (indice in 0 until novos.length()) {
            val evento = novos.getJSONObject(indice)
            val id = evento.getLong("id")
            val descricao = evento.getString("descricao")
            registros.add(0, EventoCasa(evento.getLong("instante"), descricao))
            ultimo = maxOf(ultimo, id)
            notificarTutor(descricao, id)
        }

        if (novos.length() > 0) {
            val recentes = registros.take(50)
            val json = JSONArray()
            recentes.forEach { json.put(JSONObject().put("instante", it.instante).put("descricao", it.descricao)) }
            preferencias.edit().putLong("cursor", ultimo).putString("recebidos", json.toString()).apply()
            recebidos.value = recentes
        }
    }

    private fun repetirEnvio(atual: SessaoCasa) {
        while (true) {
            val registro = synchronized(this) { fila().optJSONObject(0) } ?: return
            if (registro.getString("vinculo") == vinculo.value.id) {
                requisicao(atual.servidor, "/api/atividades", "POST", registro, atual.token)
            }
            synchronized(this) {
                val registros = fila()
                if (registros.optJSONObject(0)?.optString("chave") == registro.optString("chave")) {
                    registros.remove(0)
                    salvarFila(registros)
                }
            }
        }
    }

    private fun atualizarVinculo(resultado: JSONObject) {
        val anterior = vinculo.value
        val ativo = resultado.optBoolean("vinculado")
        val id = resultado.optString("vinculo")
        val codigo = resultado.optString("codigo", anterior.codigo.takeIf { !ativo }.orEmpty())
        synchronized(this) {
            val editor = preferencias.edit().putBoolean("vinculado", ativo).putString("vinculo", id)
            if (id != anterior.id || (!ativo && anterior.ativo)) {
                editor.remove("cursor").remove("recebidos").remove("fila")
                recebidos.value = emptyList()
            }
            editor.apply()
            vinculo.value = VinculoCasa(id, ativo, codigo)
        }
    }

    private fun limparSessao() {
        synchronized(this) {
            val servidor = sessao.value?.servidor ?: SERVIDOR_EMULADOR
            preferencias.edit().clear().putString("servidor", servidor).apply()
            sessao.value = null
            vinculo.value = VinculoCasa()
            recebidos.value = emptyList()
        }
        val jobs = contexto.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        jobs.cancel(ID_JOB)
    }

    private fun carregarRecebidos(): List<EventoCasa> {
        val json = JSONArray(preferencias.getString("recebidos", "[]"))
        return List(json.length()) { indice ->
            val item = json.getJSONObject(indice)
            EventoCasa(item.getLong("instante"), item.getString("descricao"))
        }
    }

    private fun fila() = JSONArray(preferencias.getString("fila", "[]"))
    private fun salvarFila(registros: JSONArray) {
        if (registros.length() > 200) registros.remove(0)
        preferencias.edit().putString("fila", registros.toString()).apply()
    }

    fun agendarSincronizacao() {
        if (sessao.value == null) return
        val jobs = contexto.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        if (jobs.getPendingJob(ID_JOB) != null) return
        jobs.schedule(
            JobInfo.Builder(ID_JOB, ComponentName(contexto, AvisosJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(15 * 60 * 1000)
                .setPersisted(true)
                .build()
        )
    }

    private fun notificarTutor(descricao: String, id: Long) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                contexto, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        val gerente = contexto.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!gerente.areNotificationsEnabled()) return
        val canal = "atividades_pareadas"
        gerente.createNotificationChannel(NotificationChannel(canal, "Atividades da pessoa assistida", NotificationManager.IMPORTANCE_DEFAULT))
        val abrir = PendingIntent.getActivity(
            contexto,
            ID_JOB,
            Intent(contexto, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val aviso = NotificationCompat.Builder(contexto, canal)
            .setSmallIcon(R.drawable.ic_lembrete)
            .setContentTitle("Atividade no CasaSegura")
            .setContentText(descricao)
            .setStyle(NotificationCompat.BigTextStyle().bigText(descricao))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .build()
        try {
            gerente.notify(vinculo.value.id, id.toInt(), aviso)
        } catch (_: SecurityException) {
            // O evento continua disponível no histórico se o Android bloquear o aviso.
        }
    }

    private fun validarServidor(endereco: String): String {
        val base = endereco.trim().trimEnd('/')
        val uri = try { URI(base) } catch (_: Exception) { throw IOException("Informe um endereço de servidor válido.") }
        if (uri.host == null || uri.userInfo != null || uri.query != null || uri.fragment != null ||
            (uri.scheme != "https" && base != SERVIDOR_EMULADOR)
        ) throw IOException("Use um endereço HTTPS. O endereço HTTP padrão serve para o emulador.")
        return base
    }

    private fun requisicao(
        base: String,
        rota: String,
        metodo: String = "GET",
        dados: JSONObject? = null,
        token: String? = null
    ): JSONObject {
        val conexao = URI(base + rota).toURL().openConnection() as HttpURLConnection
        return try {
            conexao.instanceFollowRedirects = false
            conexao.requestMethod = metodo
            conexao.connectTimeout = 5_000
            conexao.readTimeout = 5_000
            token?.let { conexao.setRequestProperty("Authorization", "Bearer $it") }
            dados?.let {
                conexao.doOutput = true
                conexao.setRequestProperty("Content-Type", "application/json")
                conexao.outputStream.use { saida -> saida.write(it.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = conexao.responseCode
            if (status !in 200..299) {
                val resposta = conexao.errorStream?.bufferedReader()?.use { it.readText() }
                val mensagem = try { resposta?.let { JSONObject(it).optString("erro") } } catch (_: Exception) { null }
                throw RespostaServidor(status, mensagem?.takeIf { it.isNotBlank() } ?: "O servidor retornou erro $status.")
            }
            try {
                JSONObject(conexao.inputStream.bufferedReader().use { it.readText() })
            } catch (_: org.json.JSONException) {
                throw IOException("O servidor enviou uma resposta inválida.")
            }
        } finally {
            conexao.disconnect()
        }
    }

    internal class RespostaServidor(val status: Int, mensagem: String) : IOException(mensagem)

    companion object {
        const val SERVIDOR_EMULADOR = "http://10.0.2.2:3000"
        private const val ID_JOB = 1201

        @Volatile private var instancia: AcessoCasa? = null

        fun obter(contexto: Context): AcessoCasa =
            instancia ?: synchronized(this) {
                instancia ?: AcessoCasa(contexto.applicationContext).also { instancia = it }
            }
    }
}

class AvisosJobService : JobService() {
    private var tarefa: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        tarefa = CoroutineScope(Dispatchers.Main).launch {
            AcessoCasa.obter(this@AvisosJobService).sincronizar()
            if (isActive) jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        tarefa?.cancel()
        tarefa = null
        return true
    }
}