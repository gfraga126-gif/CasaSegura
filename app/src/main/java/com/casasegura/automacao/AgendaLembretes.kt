package com.casasegura.automacao

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.time.LocalTime
import java.time.ZonedDateTime

internal enum class TipoLembrete(
    val id: Int,
    val titulo: String,
    val icone: String,
    val horarioInicial: LocalTime,
    val mensagem: String
) {
    CAFE(1, "Hora do café", "☕", LocalTime.of(8, 0), "Está na hora do café."),
    ALMOCO(2, "Hora do almoço", "🍽️", LocalTime.NOON, "Está na hora do almoço."),
    JANTAR(3, "Hora do jantar", "🍲", LocalTime.of(19, 0), "Está na hora do jantar."),
    REMEDIO(4, "Hora do remédio", "💊", LocalTime.of(20, 0), "Confira o horário do seu remédio.")
}

internal data class Lembrete(val tipo: TipoLembrete, val horario: LocalTime, val ativo: Boolean)

internal class AgendaLembretes(private val contexto: Context) {
    private val preferencias = contexto.getSharedPreferences("lembretes_diarios", Context.MODE_PRIVATE)
    private val alarmes = contexto.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val notificacoes = contexto.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun carregar(tipo: TipoLembrete): Lembrete {
        val minutos = preferencias.getInt(
            "minutos_${tipo.id}", tipo.horarioInicial.toSecondOfDay() / 60
        ).coerceIn(0, 1439)
        return Lembrete(
            tipo,
            LocalTime.of(minutos / 60, minutos % 60),
            preferencias.getBoolean("ativo_${tipo.id}", false)
        )
    }

    fun salvar(lembrete: Lembrete) {
        preferencias.edit()
            .putInt("minutos_${lembrete.tipo.id}", lembrete.horario.toSecondOfDay() / 60)
            .putBoolean("ativo_${lembrete.tipo.id}", lembrete.ativo)
            .apply()
        if (!lembrete.ativo) {
            preferencias.edit().remove("ativacao_pendente_${lembrete.tipo.id}").apply()
            notificacoes.cancel(20 + lembrete.tipo.id)
        }
        reagendar(lembrete)
    }

    fun reagendar(lembrete: Lembrete) {
        val operacao = PendingIntent.getBroadcast(
            contexto,
            lembrete.tipo.id,
            Intent(contexto, LembreteReceiver::class.java)
                .setAction(ACAO_LEMBRETE)
                .putExtra(EXTRA_TIPO, lembrete.tipo.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        alarmes.cancel(operacao)
        if (!lembrete.ativo) return

        val agora = ZonedDateTime.now()
        val hoje = agora.toLocalDate()
        val horarioHoje = hoje.atTime(lembrete.horario).atZone(agora.zone)
        val proximo = if (horarioHoje.isAfter(agora)) horarioHoje
        else hoje.plusDays(1).atTime(lembrete.horario).atZone(agora.zone)
        alarmes.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, proximo.toInstant().toEpochMilli(), operacao)
    }

    fun podeNotificar(canal: String = CANAL): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false

        return notificacoes.areNotificationsEnabled() &&
            notificacoes.getNotificationChannel(canal)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun notificarAtivacao(lembrete: Lembrete): Boolean {
        val entregue = enviar(
            20 + lembrete.tipo.id,
            "Lembrete ativado",
            "${lembrete.tipo.titulo} · todos os dias às ${lembrete.horario}.",
            canal = CANAL_ATIVACAO
        )
        preferencias.edit().putBoolean("ativacao_pendente_${lembrete.tipo.id}", !entregue).apply()
        return entregue
    }

    fun enviarAtivacoesPendentes() {
        TipoLembrete.entries.forEach { tipo ->
            if (preferencias.getBoolean("ativacao_pendente_${tipo.id}", false)) {
                val lembrete = carregar(tipo)
                if (lembrete.ativo) notificarAtivacao(lembrete)
            }
        }
    }

    fun notificar(tipo: TipoLembrete, teste: Boolean = false): Boolean =
        enviar(
            if (teste) ID_TESTE else tipo.id,
            if (teste) "Teste do Tutor em Casa" else tipo.titulo,
            if (teste) "As notificações de lembretes estão funcionando." else tipo.mensagem
        )

    private fun enviar(id: Int, titulo: String, texto: String, canal: String = CANAL): Boolean {
        notificacoes.createNotificationChannel(
            if (canal == CANAL_ATIVACAO) {
                NotificationChannel(canal, "Confirmação de lembretes", NotificationManager.IMPORTANCE_HIGH)
            } else {
                NotificationChannel(canal, "Lembretes da casa", NotificationManager.IMPORTANCE_DEFAULT)
            }
        )
        if (!podeNotificar(canal)) return false

        val abrirApp = PendingIntent.getActivity(
            contexto,
            0,
            Intent(contexto, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notificacao = NotificationCompat.Builder(contexto, canal)
            .setSmallIcon(R.drawable.ic_lembrete)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setContentIntent(abrirApp)
            .setAutoCancel(true)
            .build()

        try {
            notificacoes.notify(id, notificacao)
            return true
        } catch (_: SecurityException) {
            return false
        }
    }

    companion object {
        const val ACAO_LEMBRETE = "com.casasegura.automacao.LEMBRETE_DIARIO"
        const val EXTRA_TIPO = "tipo_lembrete"
        private const val CANAL = "lembretes_da_casa"
        private const val CANAL_ATIVACAO = "confirmacao_lembretes"
        private const val ID_TESTE = 100
    }
}

class LembreteReceiver : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intent: Intent) {
        val agenda = AgendaLembretes(contexto)
        when (intent.action) {
            AgendaLembretes.ACAO_LEMBRETE -> {
                val id = intent.getIntExtra(AgendaLembretes.EXTRA_TIPO, -1)
                val tipo = TipoLembrete.entries.firstOrNull { it.id == id } ?: return
                val lembrete = agenda.carregar(tipo)
                if (lembrete.ativo) {
                    agenda.reagendar(lembrete)
                    agenda.notificar(tipo)
                }
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                TipoLembrete.entries.forEach { agenda.reagendar(agenda.carregar(it)) }
            }
        }
    }
}