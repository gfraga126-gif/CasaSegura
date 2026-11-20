package com.casasegura.automacao

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import kotlinx.coroutines.delay

@Composable
internal fun IconeAcao(icone: String, texto: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icone, Modifier.clearAndSetSemantics {}, fontSize = 32.sp, lineHeight = 40.sp)
        Text(texto, Modifier.weight(1f, fill = false), fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun RecursosAparelho(registrarAcao: (String) -> Unit) {
    ConteudoRolavel {
        TituloSecao("Recursos do aparelho", "Foto, recado de voz e teste de movimento.")
        Spacer(Modifier.height(16.dp))
        CartaoFoto(registrarAcao)
        Spacer(Modifier.height(12.dp))
        CartaoRecado(registrarAcao)
        Spacer(Modifier.height(12.dp))
        CartaoGiroscopio()
    }
}

@Composable
private fun CartaoFoto(registrarAcao: (String) -> Unit) {
    var foto by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember { mutableStateOf("A foto fica nesta tela durante a sessão.") }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { imagem ->
        if (imagem != null) {
            foto = imagem
            status = "Foto capturada."
            registrarAcao("Foto capturada no aparelho.")
        } else {
            status = "Captura cancelada."
        }
    }

    CartaoRecurso("Câmera") {
        Text(status)
        foto?.let { imagem ->
            Image(imagem.asImageBitmap(), "Foto capturada", Modifier.fillMaxWidth().heightIn(max = 220.dp))
        }
        BotaoRecurso("📷", "TIRAR FOTO") {
            try {
                camera.launch(null)
            } catch (_: ActivityNotFoundException) {
                status = "Nenhum aplicativo de câmera está disponível."
            } catch (_: SecurityException) {
                status = "O aparelho bloqueou o acesso à câmera."
            }
        }
    }
}

@Composable
private fun CartaoRecado(registrarAcao: (String) -> Unit) {
    val contexto = LocalContext.current
    val ciclo = LocalLifecycleOwner.current.lifecycle
    val acaoAtual by rememberUpdatedState(registrarAcao)
    val audio = remember(contexto) { RecadoDeVoz(contexto) { acaoAtual(it) } }
    DisposableEffect(ciclo, audio) {
        val observador = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_STOP) {
                audio.finalizarGravacao()
                audio.pararReproducao()
            }
        }
        ciclo.addObserver(observador)
        onDispose {
            ciclo.removeObserver(observador)
            audio.liberar()
        }
    }

    LaunchedEffect(audio.gravando) {
        if (audio.gravando) {
            delay(60_000)
            audio.finalizarGravacao()
        }
    }

    val permissao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
        if (concedida) audio.gravar()
        else audio.status = "Autorize o microfone para gravar um recado."
    }

    CartaoRecurso("Microfone") {
        Text(audio.status)
        if (audio.gravando) {
            BotaoRecurso("⏹", "PARAR GRAVAÇÃO", audio::finalizarGravacao)
        } else if (audio.reproduzindo) {
            BotaoRecurso("⏹", "PARAR REPRODUÇÃO", audio::pararReproducao)
        } else {
            BotaoRecurso("🎙", "GRAVAR RECADO") {
                if (ContextCompat.checkSelfPermission(contexto, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    audio.gravar()
                } else {
                    permissao.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            if (audio.temRecado) {
                BotaoRecurso("▶", "OUVIR RECADO", audio::reproduzir)
            }
        }
    }
}

private class RecadoDeVoz(private val contexto: Context, private val registrarAcao: (String) -> Unit) {
    var status by mutableStateOf("Grave um recado curto. O áudio fica no aparelho durante a sessão.")
    var temRecado by mutableStateOf(false)
        private set
    var gravando by mutableStateOf(false)
        private set
    var reproduzindo by mutableStateOf(false)
        private set

    private var gravador: MediaRecorder? = null
    private var reprodutor: MediaPlayer? = null
    private var arquivoEmGravacao: File? = null
    private var recado: File? = null

    @Suppress("DEPRECATION")
    fun gravar() {
        try {
            val arquivo = File.createTempFile("recado-", ".m4a", contexto.cacheDir)
            arquivoEmGravacao = arquivo
            val novoGravador = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(contexto)
            } else {
                MediaRecorder()
            }
            gravador = novoGravador
            novoGravador.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(arquivo.absolutePath)
                prepare()
                start()
            }
            gravando = true
            status = "Gravando… Fale perto do aparelho."
        } catch (_: Exception) {
            liberarGravador()
            arquivoEmGravacao?.delete()
            arquivoEmGravacao = null
            status = "Não foi possível iniciar a gravação. Verifique o microfone."
        }
    }

    fun finalizarGravacao() {
        val atual = gravador ?: return
        val arquivo = arquivoEmGravacao
        arquivoEmGravacao = null
        try {
            atual.stop()
            recado?.delete()
            recado = arquivo
            temRecado = true
            status = "Recado gravado. Toque em Ouvir recado."
            registrarAcao("Recado de voz gravado no aparelho.")
        } catch (_: RuntimeException) {
            arquivo?.delete()
            status = "A gravação não foi concluída. Tente novamente."
        } finally {
            liberarGravador()
        }
    }

    fun reproduzir() {
        val arquivo = recado ?: return
        try {
            val novoReprodutor = MediaPlayer()
            reprodutor = novoReprodutor
            novoReprodutor.setDataSource(arquivo.absolutePath)
            novoReprodutor.setOnCompletionListener { pararReproducao() }
            novoReprodutor.setOnErrorListener { _, _, _ ->
                pararReproducao()
                status = "Não foi possível reproduzir o recado."
                true
            }
            novoReprodutor.prepare()
            novoReprodutor.start()
            reproduzindo = true
            status = "Reproduzindo recado…"
            registrarAcao("Reprodução de recado de voz iniciada.")
        } catch (_: Exception) {
            pararReproducao()
            status = "Não foi possível reproduzir o recado."
        }
    }

    fun pararReproducao() {
        val atual = reprodutor ?: return
        reprodutor = null
        atual.release()
        reproduzindo = false
        status = "Recado pronto para ouvir."
    }

    private fun liberarGravador() {
        val atual = gravador
        gravador = null
        atual?.release()
        gravando = false
    }

    fun liberar() {
        liberarGravador()
        pararReproducao()
        arquivoEmGravacao?.delete()
        recado?.delete()
    }
}

@Composable
private fun CartaoGiroscopio() {
    val contexto = LocalContext.current
    val ciclo = LocalLifecycleOwner.current.lifecycle
    val gerenciador = remember(contexto) { contexto.getSystemService(SensorManager::class.java) }
    val giroscopio = remember(gerenciador) { gerenciador?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) }
    var status by remember { mutableStateOf("Aguardando leitura…") }

    DisposableEffect(ciclo, gerenciador, giroscopio) {
        if (gerenciador == null || giroscopio == null) {
            status = "Giroscópio indisponível neste aparelho."
            onDispose {}
        } else {
            val ouvinte = object : SensorEventListener {
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

                override fun onSensorChanged(event: SensorEvent) {
                    val (x, y, z) = event.values
                    status = if (x * x + y * y + z * z >= 0.35f * 0.35f) {
                        "Aparelho girando"
                    } else {
                        "Sem rotação detectada"
                    }
                }
            }
            val observador = LifecycleEventObserver { _, evento ->
                when (evento) {
                    Lifecycle.Event.ON_RESUME -> {
                        if (!gerenciador.registerListener(ouvinte, giroscopio, SensorManager.SENSOR_DELAY_UI)) {
                            status = "Não foi possível iniciar a leitura."
                        }
                    }
                    Lifecycle.Event.ON_PAUSE -> gerenciador.unregisterListener(ouvinte)
                    else -> Unit
                }
            }
            ciclo.addObserver(observador)
            onDispose {
                ciclo.removeObserver(observador)
                gerenciador.unregisterListener(ouvinte)
            }
        }
    }

    CartaoRecurso("Giroscópio") {
        Text("Gire o aparelho para testar o sensor de movimento.")
        Text(status, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CartaoRecurso(titulo: String, conteudo: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleLarge)
            conteudo()
        }
    }
}

@Composable
private fun BotaoRecurso(icone: String, texto: String, acao: () -> Unit) {
    Button(onClick = acao, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
        IconeAcao(icone, texto)
    }
}