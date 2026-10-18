
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private val Azul = Color(0xFF1565C0)
private val Verde = Color(0xFF00796B)
private val Vermelho = Color(0xFFC62828)
private val Fundo = Color(0xFFF2F6FA)

private const val API_URL = "http://10.0.2.2:3000"

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

suspend fun consultarServidor(): String =
    withContext(Dispatchers.IO) {
        var conexao: HttpURLConnection? = null

        try {
            conexao = URL(
                "$API_URL/api/status"
            ).openConnection() as HttpURLConnection

            conexao.requestMethod = "GET"
            conexao.connectTimeout = 5000
            conexao.readTimeout = 5000

            if (conexao.responseCode == 200) {
                val resposta = conexao.inputStream
                    .bufferedReader()
                    .use { it.readText() }

                val json = JSONObject(resposta)

                if (json.optString("servidor") == "online") {
                    "ONLINE"
                } else {
                    "RESPOSTA INESPERADA"
                }
            } else {
                "ERRO HTTP ${conexao.responseCode}"
            }
        } catch (e: Exception) {
            "DESCONECTADO"
        } finally {
            conexao?.disconnect()
        }
    }

@Composable
fun CasaSeguraApp() {
    var tela by remember { mutableStateOf("inicio") }
    var sosSimulado by remember { mutableStateOf(false) }
    var vozAtivada by remember { mutableStateOf(true) }
    var vozPronta by remember { mutableStateOf(false) }

    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()

    val estados = remember {
        mutableStateListOf(
            false, false, false,
            false, false, false
        )
    }

    var servidorStatus by remember {
        mutableStateOf("NÃO VERIFICADO")
    }
    var consultando by remember {
        mutableStateOf(false)
    }

    var mecanismoVoz by remember {
        mutableStateOf<TextToSpeech?>(null)
    }

    DisposableEffect(contexto) {
        var instancia: TextToSpeech? = null
        var ativo = true

        instancia = TextToSpeech(contexto) { resultado ->
            if (ativo && resultado == TextToSpeech.SUCCESS) {
                val idioma = instancia?.setLanguage(
                    Locale.forLanguageTag("pt-BR")
                )

                vozPronta = idioma !=
                        TextToSpeech.LANG_MISSING_DATA &&
                        idioma !=
                        TextToSpeech.LANG_NOT_SUPPORTED &&
                        idioma != null

                instancia?.setSpeechRate(0.85f)
            } else if (ativo) {
                vozPronta = false
            }
        }

        mecanismoVoz = instancia

        onDispose {
            ativo = false
            instancia?.stop()
            instancia?.shutdown()
            mecanismoVoz = null
        }
    }

    fun falar(texto: String) {
        if (vozAtivada && vozPronta) {
            mecanismoVoz?.speak(
                texto,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "casa_segura"
            )
        }
    }

    fun verificarServidor() {
        consultando = true
        servidorStatus = "CONSULTANDO..."

        escopo.launch {
            servidorStatus = consultarServidor()
            consultando = false
        }
    }

    BackHandler(enabled = tela != "inicio") {
        mecanismoVoz?.stop()
        tela = "inicio"
    }

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
                color = Color.White
            )

            Text(
                "DEMONSTRAÇÃO • ESP32 desconectado",
                color = Color.White,
                fontSize = 11.sp
            )
        }

        when (tela) {
            "inicio" -> {
                Box(Modifier.weight(1f)) {
                    ConteudoRolavel {
                        Spacer(Modifier.height(30.dp))

                        Text(
                            "BEM-VINDO AO CASA SEGURA",
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(25.dp))

                        Button(
                            onClick = {
                                tela = "morador"
                                falar("Bem-vindo à sua casa")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                        ) {
                            Text(
                                "PESSOA ASSISTIDA\nCONTROLAR MINHA CASA",
                                fontSize = 19.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(Modifier.height(20.dp))

                        Button(
                            onClick = {
                                tela = "tutor"
                                verificarServidor()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp),
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
                            "Acesso em demonstração.\n" +
                                    "Login será implementado depois.",
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            "morador" -> {
                Box(Modifier.weight(1f)) {
                    ConteudoRolavel {
                        Text(
                            "MINHA CASA",
                            fontSize = 29.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            "O que deseja fazer?",
                            fontSize = 20.sp
                        )

                        Spacer(Modifier.height(15.dp))

                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "ASSISTENTE DE VOZ",
                                    modifier = Modifier.weight(1f),
                                    fontWeight = FontWeight.Bold
                                )

                                Switch(
                                    checked = vozAtivada,
                                    onCheckedChange = {
                                        vozAtivada = it
                                        if (!it) mecanismoVoz?.stop()
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(15.dp))

                        Dispositivos.forEachIndexed { indice, nome ->
                            ControleAcessivel(
                                nome = nome,
                                aberto = estados[indice],
                                ouvir = {
                                    falar(
                                        "Este controle permite abrir " +
                                                "ou fechar $nome."
                                    )
                                },
                                acionar = {
                                    estados[indice] = !estados[indice]

                                    val comando =
                                        if (estados[indice])
                                            "Abrir" else "Fechar"

                                    falar(
                                        "$comando $nome. " +
                                                "Comando apenas simulado."
                                    )
                                }
                            )
                        }

                        Button(
                            onClick = {
                                falar(
                                    "Este botão abre o telefone " +
                                            "para ligar ao SAMU, número " +
                                            "cento e noventa e dois."
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
                                "OUVIR INSTRUÇÕES DE EMERGÊNCIA",
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        var confirmarSamu by remember {
                            mutableStateOf(false)
                        }

                        Button(
                            onClick = {
                                confirmarSamu = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(95.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF8B0000)
                            )
                        ) {
                            Text(
                                "EMERGÊNCIA GRAVE\nLIGAR SAMU 192",
                                fontSize = 19.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        if (confirmarSamu) {
                            AlertDialog(
                                onDismissRequest = {
                                    confirmarSamu = false
                                },
                                title = {
                                    Text("Ligar para o SAMU?")
                                },
                                text = {
                                    Text(
                                        "Abrir o discador com o " +
                                                "número 192? Você precisará " +
                                                "confirmar a chamada."
                                    )
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            confirmarSamu = false

                                            try {
                                                contexto.startActivity(
                                                    Intent(
                                                        Intent.ACTION_DIAL,
                                                        Uri.parse("tel:192")
                                                    )
                                                )
                                            } catch (e: ActivityNotFoundException) {
                                                falar("Discador indisponível")
                                            } catch (e: SecurityException) {
                                                falar("Falha ao abrir telefone")
                                            }
                                        }
                                    ) {
                                        Text("ABRIR DISCADOR")
                                    }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = {
                                            confirmarSamu = false
                                        }
                                    ) {
                                        Text("CANCELAR")
                                    }
                                }
                            )
                        }

                        Spacer(Modifier.height(15.dp))

                        Text(
                            "SOS e controles ainda simulados.",
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Button(
                    onClick = {
                        sosSimulado = true
                        falar(
                            "Atenção. Pedido de ajuda " +
                                    "registrado em demonstração. " +
                                    "Nenhum tutor recebeu alerta real."
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .height(80.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Vermelho
                    )
                ) {
                    Text(
                        "SOS — PRECISO DE AJUDA",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    "SOS SIMULADO — não envia notificações",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp
                )
            }

            "tutor" -> {
                Box(Modifier.weight(1f)) {
                    ConteudoRolavel {
                        Text(
                            "CENTRAL DO TUTOR",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(15.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor =
                                    if (servidorStatus == "ONLINE")
                                        Color(0xFFD9F4E2)
                                    else Color(0xFFFFE8D6)
                            )
                        ) {
                            Column(Modifier.padding(20.dp)) {
                                Text(
                                    "CONEXÃO COM O BACK-END",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )

                                Spacer(Modifier.height(8.dp))

                                Text(
                                    servidorStatus,
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(Modifier.height(12.dp))

                                Button(
                                    onClick = {
                                        verificarServidor()
                                    },
                                    enabled = !consultando,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        if (consultando)
                                            "VERIFICANDO..."
                                        else
                                            "ATUALIZAR CONEXÃO"
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(15.dp))

                        if (sosSimulado) {
                            CartaoInformacao(
                                "SOS SIMULADO",
                                "Pedido registrado somente " +
                                        "nesta sessão do aplicativo. " +
                                        "Nenhuma notificação real enviada."
                            )
                        }

                        CartaoInformacao(
                            "ESP32",
                            "Desconectado"
                        )

                        CartaoInformacao(
                            "Atividade do morador",
                            "Aguardando sensores"
                        )

                        CartaoInformacao(
                            "Portas e janelas",
                            "Sem dados reais"
                        )

                        CartaoInformacao(
                            "Notificações",
                            "Ainda não configuradas"
                        )
                    }
                }
            }
        }

        if (tela != "inicio") {
            OutlinedButton(
                onClick = {
                    mecanismoVoz?.stop()
                    tela = "inicio"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .heightIn(min = 55.dp)
            ) {
                Text("← VOLTAR À TELA INICIAL")
            }
        }
    }
}

@Composable
fun ControleAcessivel(
    nome: String,
    aberto: Boolean,
    ouvir: () -> Unit,
    acionar: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(15.dp)) {
            Text(
                nome,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = ouvir,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    "🔊 OUVIR FUNÇÃO",
                    fontSize = 17.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = acionar,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(65.dp)
            ) {
                Text(
                    if (aberto) "FECHAR" else "ABRIR",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                if (aberto)
                    "Aberto (simulado)"
                else
                    "Fechado (simulado)",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CartaoInformacao(
    titulo: String,
    descricao: String
) {
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

@Composable
fun ConteudoRolavel(
    conteudo: @Composable ColumnScope.() -> Unit
) {
    val scroll = rememberScrollState()

    Box(Modifier.fillMaxSize()) {
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
                val altura = maxHeight

                val proporcao =
                    scroll.viewportSize.toFloat() /
                            (scroll.viewportSize + scroll.maxValue).toFloat()

                val indicador =
                    (altura * proporcao)
                        .coerceAtLeast(32.dp)
                        .coerceAtMost(altura)

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
                            y = (altura - indicador) * progresso
                        )
                        .height(indicador)
                        .width(6.dp)
                        .align(Alignment.TopCenter)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Azul)
                )
            }
        }
    }
}
