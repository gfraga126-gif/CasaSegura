
package com.casasegura.automacao

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private val Azul = Color(0xFF1565C0)
private val Verde = Color(0xFF00796B)
private val Vermelho = Color(0xFFC62828)
private val Fundo = Color(0xFFF2F6FA)

private val Dispositivos = listOf(
    "Porta principal",
    "Portão",
    "Janela da sala",
    "Janela do quarto 1",
    "Janela do quarto 2",
    "Cortina"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                CasaSeguraApp()
            }
        }
    }
}

@Composable
fun CasaSeguraApp() {
    val contexto = LocalContext.current

    var tela by remember { mutableStateOf("inicio") }
    var sosSimulado by remember { mutableStateOf(false) }
    var vozPronta by remember { mutableStateOf(false) }
    var vozAtivada by remember { mutableStateOf(true) }

    val estados = remember {
        mutableStateListOf(
            false, false, false,
            false, false, false
        )
    }

    // Sistema de voz em português
    val tts = remember {
        TextToSpeech(contexto, null)
    }

    DisposableEffect(tts) {
        tts.setOnUtteranceProgressListener(null)

        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    // Configuração de idioma
    LaunchedEffect(tts) {
        // A inicialização é tratada pelo bloco abaixo.
    }

    // Tela inicial e navegação
    BackHandler(enabled = tela != "inicio") {
        tts.stop()
        tela = "inicio"
    }

    // Referência ao estado para a função de fala
    var statusVoz by remember {
        mutableStateOf("Preparando voz...")
    }

    DisposableEffect(Unit) {
        onDispose { }
    }

    // A inicialização segura do TTS é feita pelo componente
    // GerenciadorDeVoz definido no final do arquivo.
    GerenciadorDeVoz(
        ativada = vozAtivada,
        aoEstado = { pronta, status ->
            vozPronta = pronta
            statusVoz = status
        }
    ) { falar ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Fundo)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Azul)
                    .padding(16.dp)
            ) {
                Text(
                    "CASA SEGURA",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    "Automação Residencial Assistiva",
                    color = Color.White,
                    fontSize = 14.sp
                )

                Text(
                    "DEMONSTRAÇÃO • ESP32 desconectado",
                    color = Color.White,
                    fontSize = 11.sp
                )
            }

            when (tela) {
                "inicio" -> {
                    Box(modifier = Modifier.weight(1f)) {
                        ConteudoComRolagem {
                            TelaInicial(
                                abrirMorador = {
                                    tela = "morador"
                                    falar(
                                        "Bem-vindo à sua casa. " +
                                                "Escolha o que deseja fazer."
                                    )
                                },
                                abrirTutor = {
                                    tela = "tutor"
                                }
                            )
                        }
                    }
                }

                "morador" -> {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            ConteudoComRolagem {
                                TelaMorador(
                                    estados = estados,
                                    falar = falar,
                                    vozAtivada = vozAtivada,
                                    alterarVoz = {
                                        vozAtivada = it
                                    },
                                    statusVoz = statusVoz
                                )
                            }
                        }

                        // SOS sempre visível
                        Button(
                            onClick = {
                                sosSimulado = true
                                falar(
                                    "Atenção. Pedido de ajuda " +
                                            "registrado em demonstração. " +
                                            "Nenhum familiar recebeu " +
                                            "uma notificação real."
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .height(85.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Vermelho
                            )
                        ) {
                            Text(
                                "SOS — PRECISO DE AJUDA",
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }

                        Text(
                            "SOS simulado — sem envio real",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            fontSize = 11.sp
                        )
                    }
                }

                "tutor" -> {
                    Box(modifier = Modifier.weight(1f)) {
                        ConteudoComRolagem {
                            TelaTutor(sosSimulado)
                        }
                    }
                }
            }

            if (tela != "inicio") {
                OutlinedButton(
                    onClick = {
                        falar("Voltando à tela inicial")
                        tela = "inicio"
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 12.dp,
                            vertical = 8.dp
                        )
                        .height(55.dp)
                ) {
                    Text(
                        "← VOLTAR",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun GerenciadorDeVoz(
    ativada: Boolean,
    aoEstado: (Boolean, String) -> Unit,
    conteudo: @Composable ((String) -> Unit) -> Unit
) {
    val contexto = LocalContext.current
    var pronto by remember { mutableStateOf(false) }
    var status by remember {
        mutableStateOf("Preparando voz...")
    }

    val tts = remember {
        var instancia: TextToSpeech? = null

        instancia = TextToSpeech(contexto) { resultado ->
            if (resultado == TextToSpeech.SUCCESS) {
                val idioma = instancia?.setLanguage(
                    Locale("pt", "BR")
                )

                if (
                    idioma == TextToSpeech.LANG_MISSING_DATA ||
                    idioma == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    pronto = false
                    status = "Voz em português indisponível"
                } else {
                    instancia?.setSpeechRate(0.85f)
                    pronto = true
                    status = "Voz em português pronta"
                }
            } else {
                pronto = false
                status = "Não foi possível iniciar a voz"
            }
        }

        instancia
    }

    DisposableEffect(tts) {
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    LaunchedEffect(pronto, status) {
        aoEstado(pronto, status)
    }

    LaunchedEffect(ativada) {
        if (!ativada) {
            tts?.stop()
        }
    }

    val falar: (String) -> Unit = { texto ->
        if (ativada && pronto) {
            tts?.speak(
                texto,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "casa_segura_voz"
            )
        }
    }

    conteudo(falar)
}

@Composable
fun ConteudoComRolagem(
    conteudo: @Composable ColumnScope.() -> Unit
) {
    val scroll = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(
                    start = 16.dp,
                    end = 26.dp,
                    top = 16.dp,
                    bottom = 16.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = conteudo
        )

        if (scroll.maxValue > 0) {
            BoxWithConstraints(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(14.dp)
                    .padding(
                        end = 4.dp,
                        top = 12.dp,
                        bottom = 12.dp
                    )
            ) {
                val alturaTrilho = maxHeight

                val proporcao =
                    scroll.viewportSize.toFloat() /
                            (scroll.viewportSize + scroll.maxValue)
                                .toFloat()

                val alturaIndicador =
                    (alturaTrilho * proporcao)
                        .coerceAtLeast(32.dp)
                        .coerceAtMost(alturaTrilho)

                val progresso =
                    scroll.value.toFloat() /
                            scroll.maxValue.toFloat()

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(6.dp)
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.LightGray)
                )

                Box(
                    modifier = Modifier
                        .offset(
                            y = (alturaTrilho - alturaIndicador) *
                                    progresso
                        )
                        .height(alturaIndicador)
                        .width(6.dp)
                        .align(Alignment.TopCenter)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Azul)
                )
            }
        }
    }
}

@Composable
fun TelaInicial(
    abrirMorador: () -> Unit,
    abrirTutor: () -> Unit
) {
    Spacer(Modifier.height(25.dp))

    Text(
        "BEM-VINDO AO CASA SEGURA",
        fontSize = 23.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(15.dp))

    Text(
        "Escolha como deseja acessar",
        fontSize = 19.sp,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(35.dp))

    Button(
        onClick = abrirMorador,
        modifier = Modifier
            .fillMaxWidth()
            .height(115.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Text(
            "PESSOA ASSISTIDA\nCONTROLAR MINHA CASA",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }

    Spacer(Modifier.height(20.dp))

    Button(
        onClick = abrirTutor,
        modifier = Modifier
            .fillMaxWidth()
            .height(115.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Verde
        )
    ) {
        Text(
            "FAMILIAR / TUTOR\nACOMPANHAR RESIDÊNCIA",
            fontSize = 19.sp,
            textAlign = TextAlign.Center
        )
    }

    Spacer(Modifier.height(25.dp))

    Text(
        "Versão de demonstração. Login será adicionado.",
        textAlign = TextAlign.Center
    )
}

@Composable
fun TelaMorador(
    estados: SnapshotStateList<Boolean>,
    falar: (String) -> Unit,
    vozAtivada: Boolean,
    alterarVoz: (Boolean) -> Unit,
    statusVoz: String
) {
    val contexto = LocalContext.current
    var confirmarSamu by remember {
        mutableStateOf(false)
    }

    Text(
        "MINHA CASA",
        fontSize = 29.sp,
        fontWeight = FontWeight.Bold
    )

    Spacer(Modifier.height(8.dp))

    Text(
        "Toque para ouvir ou controlar",
        fontSize = 19.sp,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(12.dp))

    // Liga ou desliga a voz
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ASSISTENTE DE VOZ",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold
                )

                Switch(
                    checked = vozAtivada,
                    onCheckedChange = alterarVoz
                )
            }

            Text(
                if (vozAtivada) statusVoz
                else "Voz desativada",
                fontSize = 13.sp
            )
        }
    }

    Spacer(Modifier.height(15.dp))

    Dispositivos.forEachIndexed { indice, nome ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(15.dp)
            ) {
                Text(
                    nome,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(10.dp))

                // Explica a função antes do comando
                OutlinedButton(
                    onClick = {
                        falar(
                            "Este botão permite abrir " +
                                    "ou fechar $nome."
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        "🔊 OUVIR FUNÇÃO",
                        fontSize = 18.sp
                    )
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        val abrindo = !estados[indice]
                        estados[indice] = abrindo

                        val acao = if (abrindo) {
                            "Abrir"
                        } else {
                            "Fechar"
                        }

                        falar(
                            "$acao $nome. " +
                                    "Comando simulado."
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(65.dp)
                ) {
                    Text(
                        if (estados[indice])
                            "FECHAR"
                        else
                            "ABRIR",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    if (estados[indice])
                        "Aberto (simulação)"
                    else
                        "Fechado (simulação)",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    Button(
        onClick = {
            falar(
                "Este botão é destinado a uma " +
                        "emergência médica grave. " +
                        "Ele abre o telefone para ligar " +
                        "ao SAMU, número cento e noventa e dois."
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Verde
        )
    ) {
        Text(
            "🔊 OUVIR INSTRUÇÕES DE EMERGÊNCIA",
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
    }

    Spacer(Modifier.height(12.dp))

    Button(
        onClick = {
            confirmarSamu = true
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF8B0000)
        )
    ) {
        Text(
            "EMERGÊNCIA GRAVE\nLIGAR SAMU 192",
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )
    }

    Spacer(Modifier.height(20.dp))

    Text(
        "Os controles e o SOS ainda são simulados. " +
                "Nenhuma notificação real é enviada.",
        textAlign = TextAlign.Center,
        fontSize = 14.sp
    )

    if (confirmarSamu) {
        AlertDialog(
            onDismissRequest = {
                confirmarSamu = false
            },
            title = {
                Text("Ligar para o SAMU 192?")
            },
            text = {
                Text(
                    "O discador será aberto com " +
                            "o número 192. Confirme a chamada."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmarSamu = false

                        falar("Abrindo o telefone para o SAMU.")

                        try {
                            contexto.startActivity(
                                Intent(
                                    Intent.ACTION_DIAL,
                                    Uri.parse("tel:192")
                                )
                            )
                        } catch (e: ActivityNotFoundException) {
                            falar("Discador indisponível.")
                        } catch (e: SecurityException) {
                            falar("Não foi possível abrir o telefone.")
                        }
                    }
                ) {
                    Text("ABRIR DISCADOR")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmarSamu = false }
                ) {
                    Text("CANCELAR")
                }
            }
        )
    }
}

@Composable
fun TelaTutor(sosSimulado: Boolean) {
    Text(
        "CENTRAL DO TUTOR",
        fontSize = 25.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(20.dp))

    if (sosSimulado) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFFFCDD2)
            )
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "SOS SIMULADO",
                    fontSize = 22.sp,
                    color = Vermelho,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "Foi pressionado o botão SOS nesta " +
                            "sessão. Nenhuma notificação real foi enviada."
                )
            }
        }
    }

    Cartao("Conexão", "ESP32 desconectado")
    Cartao("Atividade do morador", "Aguardando sensores")
    Cartao("Portas e janelas", "Sem dados reais")
    Cartao("Sensor de chuva", "Aguardando conexão")
    Cartao("Notificações", "Ainda não configuradas")
    Cartao("Acesso remoto", "Será implementado no back-end")
}

@Composable
fun Cartao(titulo: String, descricao: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                titulo,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(6.dp))

            Text(descricao)
        }
    }
}
