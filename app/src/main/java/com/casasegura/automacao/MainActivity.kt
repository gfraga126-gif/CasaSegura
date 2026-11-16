
package com.casasegura.automacao

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

// CORES DO APLICATIVO
private val AzulEscuro = Color(0xFF103B70)
private val Azul = Color(0xFF1769C2)
private val AzulClaro = Color(0xFF38A5ED)
private val Verde = Color(0xFF009B80)
private val Vermelho = Color(0xFFD32F2F)
private val Fundo = Color(0xFFF0F6FD)
private val TextoEscuro = Color(0xFF172D49)

// Servidor local do computador, acessado pelo emulador
private const val API_URL = "http://10.0.2.2:3000"

private val dispositivos = listOf(
    "Porta principal",
    "Portão",
    "Janela da sala",
    "Janela do quarto 1",
    "Janela do quarto 2",
    "Cortina"
)

private val icones = listOf(
    "🚪", "🏡", "🪟", "🪟", "🪟", "🪟"
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

// CONSULTA AO SERVIDOR
private suspend fun consultarServidor(): String =
    withContext(Dispatchers.IO) {
        var conexao: HttpURLConnection? = null

        try {
            conexao = URL("$API_URL/api/status")
                .openConnection() as HttpURLConnection

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
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()

    var tela by remember { mutableStateOf("inicio") }
    var vozAtivada by remember { mutableStateOf(true) }
    var vozPronta by remember { mutableStateOf(false) }
    var mecanismoVoz by remember {
        mutableStateOf<TextToSpeech?>(null)
    }

    var sosSimulado by remember { mutableStateOf(false) }
    var confirmarSamu by remember { mutableStateOf(false) }

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

    var localizacaoTexto by remember {
        mutableStateOf("Localização ainda não consultada")
    }
    var consultandoGps by remember {
        mutableStateOf(false)
    }
    var latitude by remember {
        mutableStateOf<Double?>(null)
    }
    var longitude by remember {
        mutableStateOf<Double?>(null)
    }
    var mensagemMapa by remember {
        mutableStateOf("")
    }

    val cancelamentoGps = remember {
        mutableStateOf<CancellationSignal?>(null)
    }

    // VOZ
    DisposableEffect(contexto) {
        var ativo = true
        var instancia: TextToSpeech? = null

        instancia = TextToSpeech(contexto) { resultado ->
            if (ativo && resultado == TextToSpeech.SUCCESS) {
                val idioma = instancia?.setLanguage(
                    Locale.forLanguageTag("pt-BR")
                )
                vozPronta =
                    idioma != null &&
                            idioma != TextToSpeech.LANG_MISSING_DATA &&
                            idioma != TextToSpeech.LANG_NOT_SUPPORTED

                instancia?.setSpeechRate(0.85f)
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

    DisposableEffect(Unit) {
        onDispose {
            cancelamentoGps.value?.cancel()
        }
    }

    fun falar(mensagem: String) {
        if (vozAtivada && vozPronta) {
            mecanismoVoz?.speak(
                mensagem,
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

    // GPS
    fun atualizarGps() {
        val fina = ContextCompat.checkSelfPermission(
            contexto,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val aproximada = ContextCompat.checkSelfPermission(
            contexto,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fina && !aproximada) {
            localizacaoTexto = "Permissão de localização não concedida"
            return
        }

        try {
            val gerenciador = contexto.getSystemService(
                Context.LOCATION_SERVICE
            ) as LocationManager

            val provedor = when {
                fina && gerenciador.isProviderEnabled(
                    LocationManager.GPS_PROVIDER
                ) -> LocationManager.GPS_PROVIDER

                gerenciador.isProviderEnabled(
                    LocationManager.NETWORK_PROVIDER
                ) -> LocationManager.NETWORK_PROVIDER

                else -> null
            }

            if (provedor == null) {
                localizacaoTexto = "Ative a localização do aparelho."
                return
            }

            cancelamentoGps.value?.cancel()
            val sinal = CancellationSignal()
            cancelamentoGps.value = sinal

            consultandoGps = true
            latitude = null
            longitude = null
            mensagemMapa = ""
            localizacaoTexto = "Buscando localização..."

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                gerenciador.getCurrentLocation(
                    provedor,
                    sinal,
                    ContextCompat.getMainExecutor(contexto)
                ) { local: Location? ->
                    consultandoGps = false
                    cancelamentoGps.value = null

                    if (local != null) {
                        latitude = local.latitude
                        longitude = local.longitude
                        localizacaoTexto =
                            "Latitude: ${local.latitude}\n" +
                                    "Longitude: ${local.longitude}\n" +
                                    "Precisão estimada: ${local.accuracy.toInt()} metros"
                    } else {
                        localizacaoTexto =
                            "Localização indisponível. Tente novamente."
                    }
                }
            } else {
                consultandoGps = false
                localizacaoTexto =
                    "Esta consulta GPS requer Android 11 ou superior."
            }
        } catch (e: Exception) {
            consultandoGps = false
            localizacaoTexto = "Erro ao consultar GPS."
        }
    }

    val pedirPermissao = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultado ->
        val autorizado =
            resultado[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    resultado[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (autorizado) {
            atualizarGps()
        } else {
            localizacaoTexto = "Permissão de localização recusada"
        }
    }

    fun solicitarGps() {
        val fina = ContextCompat.checkSelfPermission(
            contexto,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val aproximada = ContextCompat.checkSelfPermission(
            contexto,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fina || aproximada) {
            atualizarGps()
        } else {
            pedirPermissao.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // GOOGLE MAPS
    fun abrirMapa() {
        val lat = latitude
        val lon = longitude

        if (lat == null || lon == null) {
            mensagemMapa = "Atualize o GPS antes de abrir o mapa."
            return
        }

        val endereco =
            "https://www.google.com/maps/search/?api=1&query=$lat,$lon"

        try {
            contexto.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(endereco)
                )
            )
            mensagemMapa = ""
        } catch (e: Exception) {
            mensagemMapa =
                "Não foi possível abrir o mapa. Verifique o navegador."
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
        // CABEÇALHO
        CabecalhoCasaSegura()

        when (tela) {
            "inicio" -> {
                Box(Modifier.weight(1f)) {
                    ConteudoRolavel {
                        Spacer(Modifier.height(12.dp))

                        Text(
                            "Bem-vindo!",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextoEscuro
                        )
                        Text(
                            "Escolha como deseja acessar o CasaSegura.",
                            fontSize = 16.sp,
                            color = Color.DarkGray,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(24.dp))

                        BotaoAcesso(
                            icone = "🏠",
                            titulo = "Pessoa Assistida",
                            subtitulo = "Controlar minha casa",
                            corInicial = AzulClaro,
                            corFinal = Azul,
                            aoClicar = {
                                tela = "morador"
                                falar("Bem-vindo à sua casa")
                            }
                        )

                        Spacer(Modifier.height(16.dp))

                        BotaoAcesso(
                            icone = "🛡️",
                            titulo = "Familiar / Tutor",
                            subtitulo = "Acompanhar residência",
                            corInicial = Color(0xFF26C8A7),
                            corFinal = Verde,
                            aoClicar = {
                                tela = "tutor"
                                verificarServidor()
                            }
                        )

                        Spacer(Modifier.height(26.dp))

                        Text(
                            "RECURSOS DO APLICATIVO",
                            fontWeight = FontWeight.Bold,
                            color = TextoEscuro
                        )

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            RecursoVisual("🔊", "Voz")
                            RecursoVisual("🏡", "Casa")
                            RecursoVisual("📍", "GPS")
                            RecursoVisual("🆘", "SOS")
                        }

                        Spacer(Modifier.height(24.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Mais autonomia e segurança",
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AzulEscuro
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Tecnologia para facilitar o dia a dia " +
                                            "de quem precisa de uma casa mais acessível.",
                                    textAlign = TextAlign.Center,
                                    color = Color.DarkGray
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Text(
                            "Protótipo acadêmico • Controles demonstrativos",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            "morador" -> {
                Box(Modifier.weight(1f)) {
                    ConteudoRolavel {
                        TituloSecao(
                            "Minha casa",
                            "Controle os dispositivos com facilidade."
                        )

                        Spacer(Modifier.height(16.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🔊", fontSize = 30.sp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Assistente de voz",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = TextoEscuro
                                    )
                                    Text(
                                        if (vozAtivada)
                                            "Instruções ativadas"
                                        else
                                            "Instruções desativadas",
                                        fontSize = 13.sp
                                    )
                                }
                                Switch(
                                    checked = vozAtivada,
                                    onCheckedChange = {
                                        vozAtivada = it
                                        if (!it) mecanismoVoz?.stop()
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        Text(
                            "DISPOSITIVOS DA CASA",
                            fontWeight = FontWeight.Bold,
                            color = TextoEscuro
                        )

                        Spacer(Modifier.height(12.dp))

                        dispositivos.forEachIndexed { indice, nome ->
                            ControleAcessivel(
                                icone = icones[indice],
                                nome = nome,
                                aberto = estados[indice],
                                ouvir = {
                                    falar(
                                        "Este controle permite abrir ou fechar $nome."
                                    )
                                },
                                acionar = {
                                    estados[indice] = !estados[indice]
                                    val comando =
                                        if (estados[indice]) "Abrir" else "Fechar"
                                    falar(
                                        "$comando $nome. Controle demonstrativo."
                                    )
                                }
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        Text(
                            "EMERGÊNCIA",
                            fontWeight = FontWeight.Bold,
                            color = TextoEscuro
                        )

                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = {
                                falar(
                                    "Em uma emergência grave, use o botão " +
                                            "para abrir o discador do SAMU, número 192."
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Verde
                            )
                        ) {
                            Text(
                                "🔊 OUVIR INSTRUÇÕES DE EMERGÊNCIA",
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = { confirmarSamu = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(82.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Vermelho
                            )
                        ) {
                            Text(
                                "☎ EMERGÊNCIA GRAVE\nLIGAR SAMU 192",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Text(
                            "Os controles da casa e o SOS são demonstrativos.",
                            textAlign = TextAlign.Center,
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }

                Button(
                    onClick = {
                        sosSimulado = true
                        falar(
                            "Pedido de ajuda registrado em demonstração. " +
                                    "Nenhuma notificação real foi enviada."
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .height(76.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Vermelho
                    )
                ) {
                    Text(
                        "🆘 SOS — PRECISO DE AJUDA",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    "SOS DEMONSTRATIVO — não envia alertas",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    color = Color.DarkGray
                )
            }

            "tutor" -> {
                Box(Modifier.weight(1f)) {
                    ConteudoRolavel {
                        TituloSecao(
                            "Central do Tutor",
                            "Informações e acompanhamento do sistema."
                        )

                        Spacer(Modifier.height(18.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White
                            )
                        ) {
                            Column(Modifier.padding(18.dp)) {
                                Text(
                                    "🌐 CONEXÃO COM O SERVIDOR",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = TextoEscuro
                                )

                                Spacer(Modifier.height(12.dp))

                                Text(
                                    servidorStatus,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (servidorStatus == "ONLINE")
                                        Verde else Vermelho
                                )

                                Spacer(Modifier.height(12.dp))

                                Button(
                                    onClick = { verificarServidor() },
                                    enabled = !consultando,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
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

                        Spacer(Modifier.height(16.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White
                            )
                        ) {
                            Column(Modifier.padding(18.dp)) {
                                Text(
                                    "📍 LOCALIZAÇÃO DO APARELHO",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextoEscuro
                                )

                                Spacer(Modifier.height(12.dp))

                                Text(
                                    localizacaoTexto,
                                    fontSize = 15.sp
                                )

                                Spacer(Modifier.height(14.dp))

                                Button(
                                    onClick = { solicitarGps() },
                                    enabled = !consultandoGps,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(
                                        if (consultandoGps)
                                            "BUSCANDO GPS..."
                                        else
                                            "ATUALIZAR LOCALIZAÇÃO"
                                    )
                                }

                                Spacer(Modifier.height(8.dp))

                                OutlinedButton(
                                    onClick = { abrirMapa() },
                                    enabled =
                                        latitude != null &&
                                                longitude != null,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("🗺️ ABRIR NO MAPA")
                                }

                                if (mensagemMapa.isNotEmpty()) {
                                    Text(
                                        mensagemMapa,
                                        color = Vermelho
                                    )
                                }

                                Spacer(Modifier.height(8.dp))

                                Text(
                                    "A localização é consultada somente " +
                                            "neste aparelho e com autorização.",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        if (sosSimulado) {
                            CartaoInformacao(
                                "🆘 SOS SIMULADO",
                                "Pedido registrado nesta sessão. " +
                                        "Nenhuma notificação real foi enviada."
                            )
                        }

                        CartaoInformacao(
                            "🏡 ESP32",
                            "Desconectado"
                        )

                        CartaoInformacao(
                            "📊 Atividade do morador",
                            "Aguardando integração com sensores"
                        )

                        CartaoInformacao(
                            "🚪 Portas e janelas",
                            "Sem dados reais"
                        )

                        CartaoInformacao(
                            "🔔 Notificações",
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
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .heightIn(min = 54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    "← VOLTAR À TELA INICIAL",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (confirmarSamu) {
        AlertDialog(
            onDismissRequest = { confirmarSamu = false },
            title = { Text("Ligar para o SAMU?") },
            text = {
                Text(
                    "O discador será aberto com o número 192. " +
                            "Você precisará confirmar a chamada."
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
                        } catch (e: Exception) {
                            falar("Não foi possível abrir o discador")
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

// CABEÇALHO MODERNO
@Composable
fun CabecalhoCasaSegura() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(AzulEscuro, Azul, AzulClaro)
                )
            )
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "⌂",
                fontSize = 43.sp,
                color = Color.White
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "CasaSegura",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    "Automação Residencial Assistiva",
                    fontSize = 12.sp,
                    color = Color.White
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            "Tecnologia, conforto e segurança",
            color = Color.White,
            fontSize = 13.sp
        )
    }
}

// BOTÕES DA TELA INICIAL
@Composable
fun BotaoAcesso(
    icone: String,
    titulo: String,
    subtitulo: String,
    corInicial: Color,
    corFinal: Color,
    aoClicar: () -> Unit
) {
    Button(
        onClick = aoClicar,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 116.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = corFinal
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(corInicial, corFinal)
                    )
                )
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icone, fontSize = 35.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    titulo,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    subtitulo,
                    fontSize = 14.sp,
                    color = Color.White
                )
            }
            Text(
                "›",
                fontSize = 35.sp,
                color = Color.White
            )
        }
    }
}

@Composable
fun RecursoVisual(
    icone: String,
    nome: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Text(icone, fontSize = 28.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            nome,
            fontSize = 13.sp,
            color = TextoEscuro
        )
    }
}

@Composable
fun TituloSecao(
    titulo: String,
    subtitulo: String
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            titulo,
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold,
            color = TextoEscuro
        )
        Spacer(Modifier.height(5.dp))
        Text(
            subtitulo,
            fontSize = 15.sp,
            color = Color.DarkGray
        )
    }
}

// CONTROLE DOS DISPOSITIVOS
@Composable
fun ControleAcessivel(
    icone: String,
    nome: String,
    aberto: Boolean,
    ouvir: () -> Unit,
    acionar: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {
        Column(Modifier.padding(17.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(icone, fontSize = 30.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        nome,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextoEscuro
                    )
                    Text(
                        if (aberto)
                            "Aberto (simulado)"
                        else
                            "Fechado (simulado)",
                        fontSize = 13.sp,
                        color = if (aberto) Verde else Color.Gray
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            OutlinedButton(
                onClick = ouvir,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("🔊 OUVIR FUNÇÃO")
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = acionar,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (aberto) Verde else Azul
                )
            ) {
                Text(
                    if (aberto) "FECHAR" else "ABRIR",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// CARTÕES DA CENTRAL DO TUTOR
@Composable
fun CartaoInformacao(
    titulo: String,
    descricao: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                titulo,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextoEscuro
            )
            Spacer(Modifier.height(7.dp))
            Text(
                descricao,
                color = Color.DarkGray
            )
        }
    }
}

// ROLAGEM DAS TELAS
@Composable
fun ConteudoRolavel(
    conteudo: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = conteudo
    )
}
