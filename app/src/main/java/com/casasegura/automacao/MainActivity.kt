
package com.casasegura.automacao

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

private val AzulEscuro = Color(0xFF171936)
private val Azul = Color(0xFF40369E)
private val AzulClaro = Color(0xFF5848C2)
private val Verde = Color(0xFF087667)
private val Vermelho = Color(0xFFB72137)
private val Fundo = Color(0xFFF5F3FC)
private val TextoEscuro = Color(0xFF24203B)

// Servidor local do computador, acessado pelo emulador
private const val API_URL = "http://10.0.2.2:3000"
private const val PERMISSAO_REDE_LOCAL = "android.permission.ACCESS_LOCAL_NETWORK"

private enum class AcaoDispositivo(
    val ativar: String,
    val desativar: String,
    val ativo: String,
    val inativo: String
) {
    ABERTURA("Abrir", "Fechar", "Aberto", "Fechado"),
    ENERGIA("Ligar", "Desligar", "Ligado", "Desligado"),
    CORTINA("Subir", "Descer", "Recolhida", "Estendida")
}

private enum class AjusteDispositivo(val inicial: Int) {
    TEMPERATURA(24),
    INTENSIDADE(100),
    ABERTURA_JANELA(0)
}

private data class Dispositivo(
    val nome: String,
    val icone: String,
    val acao: AcaoDispositivo = AcaoDispositivo.ENERGIA,
    val ajuste: AjusteDispositivo? = null
)

private const val AR_SALA = "Ar-condicionado da sala"
private const val LUZ_EXTERNA = "Iluminação externa"
private const val LUZ_SALA = "Luz da sala"
private const val LUZ_COZINHA = "Luz da cozinha"
private const val LUZ_GARAGEM = "Luz da garagem"
private const val LUZ_QUARTO_1 = "Luz do quarto 1"

private val dispositivosPorSecao = linkedMapOf(
    "PORTAS, JANELAS E CORTINA" to listOf(
        Dispositivo("Porta principal", "🚪", AcaoDispositivo.ABERTURA),
        Dispositivo("Portão", "🏡", AcaoDispositivo.ABERTURA),
        Dispositivo("Janela da sala", "🪟", AcaoDispositivo.ABERTURA, AjusteDispositivo.ABERTURA_JANELA),
        Dispositivo("Janela do quarto 1", "🪟", AcaoDispositivo.ABERTURA, AjusteDispositivo.ABERTURA_JANELA),
        Dispositivo("Janela do quarto 2", "🪟", AcaoDispositivo.ABERTURA, AjusteDispositivo.ABERTURA_JANELA),
        Dispositivo("Cortina", "🪟", AcaoDispositivo.CORTINA)
    ),
    "AR-CONDICIONADO" to listOf(
        Dispositivo("Ar-condicionado do quarto 1", "❄️", ajuste = AjusteDispositivo.TEMPERATURA),
        Dispositivo("Ar-condicionado do quarto 2", "❄️", ajuste = AjusteDispositivo.TEMPERATURA),
        Dispositivo(AR_SALA, "❄️", ajuste = AjusteDispositivo.TEMPERATURA)
    ),
    "ILUMINAÇÃO" to listOf(
        Dispositivo(LUZ_EXTERNA, "💡", ajuste = AjusteDispositivo.INTENSIDADE),
        Dispositivo(LUZ_QUARTO_1, "💡", ajuste = AjusteDispositivo.INTENSIDADE),
        Dispositivo("Luz do quarto 2", "💡", ajuste = AjusteDispositivo.INTENSIDADE),
        Dispositivo(LUZ_SALA, "💡", ajuste = AjusteDispositivo.INTENSIDADE),
        Dispositivo(LUZ_COZINHA, "💡", ajuste = AjusteDispositivo.INTENSIDADE),
        Dispositivo(LUZ_GARAGEM, "💡", ajuste = AjusteDispositivo.INTENSIDADE)
    )
)

private val dispositivosDaCasa = dispositivosPorSecao.values.flatten()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Azul,
                    primaryContainer = Color(0xFFEAE5FF),
                    onPrimaryContainer = TextoEscuro,
                    secondary = Verde,
                    secondaryContainer = Color(0xFFD9F2E9),
                    onSecondaryContainer = Color(0xFF164B43),
                    surface = Color.White,
                    onSurface = TextoEscuro,
                    surfaceVariant = Fundo,
                    onSurfaceVariant = Color(0xFF51496A),
                    error = Vermelho
                )
            ) {
                CasaSeguraApp()
            }
        }
    }
}

private suspend fun consultarServidor(endereco: String): String =
    withContext(Dispatchers.IO) {
        var conexao: HttpURLConnection? = null

        try {
            conexao = URL("$endereco/api/status")
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CasaSeguraApp() {
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    var tela by rememberSaveable { mutableStateOf("inicio") }
    var origemLembretes by rememberSaveable { mutableStateOf("morador") }
    if (tela == "vinculo") LaunchedEffect(Unit) { tela = "inicio" }
    var vozAtivada by remember { mutableStateOf(true) }
    var vozPronta by remember { mutableStateOf(false) }
    var mecanismoVoz by remember {
        mutableStateOf<TextToSpeech?>(null)
    }

    var sosSimulado by remember { mutableStateOf(false) }
    var confirmarSamu by remember { mutableStateOf(false) }

    val estados = remember { mutableStateMapOf<Dispositivo, Boolean>() }
    val ajustes = remember { mutableStateMapOf<Dispositivo, Int>() }
    var solarSimulada by rememberSaveable { mutableStateOf(false) }
    var confirmarSaida by rememberSaveable { mutableStateOf(false) }
    val historico = remember(contexto) { HistoricoCasa(contexto) }
    var ultimaRotina by rememberSaveable { mutableStateOf<String?>(null) }

    fun registrarAcao(descricao: String) {
        historico.registrar(descricao)
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

    var buscaGps by remember { mutableStateOf<Job?>(null) }

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

    fun executarRotina(rotina: RotinaCasa) {
        dispositivosDaCasa.forEach { dispositivo ->
            val nome = dispositivo.nome
            val ajuste = dispositivo.ajuste
            when (rotina) {
                RotinaCasa.BOA_NOITE -> when {
                    nome == LUZ_SALA || nome == LUZ_COZINHA -> estados[dispositivo] = false
                    ajuste == AjusteDispositivo.ABERTURA_JANELA -> ajustes[dispositivo] = 0
                    dispositivo.acao == AcaoDispositivo.CORTINA -> estados[dispositivo] = false
                    nome == LUZ_QUARTO_1 -> {
                        estados[dispositivo] = true
                        ajustes[dispositivo] = 20
                    }
                    else -> Unit
                }
                RotinaCasa.CHEGUEI -> {
                    if (nome == LUZ_GARAGEM || nome == LUZ_SALA || nome == AR_SALA) {
                        estados[dispositivo] = true
                    }
                }
                RotinaCasa.SAIR -> when {
                    ajuste == AjusteDispositivo.ABERTURA_JANELA -> ajustes[dispositivo] = 0
                    ajuste == AjusteDispositivo.TEMPERATURA -> estados[dispositivo] = false
                    ajuste == AjusteDispositivo.INTENSIDADE && nome != LUZ_EXTERNA ->
                        estados[dispositivo] = false
                    else -> Unit
                }
            }
        }
        val mensagem = "Rotina ${rotina.titulo} executada."
        ultimaRotina = mensagem
        registrarAcao(mensagem)
        falar(mensagem)
    }

    fun temAcessoRedeLocal() = Build.VERSION.SDK_INT < 37 ||
        ContextCompat.checkSelfPermission(contexto, PERMISSAO_REDE_LOCAL) == PackageManager.PERMISSION_GRANTED

    fun verificarServidor() {
        if (!temAcessoRedeLocal()) {
            servidorStatus = "PERMITA O ACESSO À REDE LOCAL"
            return
        }
        consultando = true
        servidorStatus = "CONSULTANDO..."

        escopo.launch {
            servidorStatus = consultarServidor(API_URL)
            consultando = false
        }
    }

    val pedirRedeLocal = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
        if (concedida) verificarServidor()
        else servidorStatus = "ACESSO À REDE LOCAL NÃO AUTORIZADO"
    }

    // GPS
    fun temLocalizacaoPrecisa() = ContextCompat.checkSelfPermission(
        contexto, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    fun atualizarGps() {
        if (consultandoGps) return
        latitude = null
        longitude = null
        mensagemMapa = ""

        if (!temLocalizacaoPrecisa()) {
            localizacaoTexto = "Autorize a localização precisa do Tutor em Casa nas permissões do Android."
            return
        }

        val gerenciador = contexto.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!gerenciador.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            localizacaoTexto = "Ative a localização do aparelho para usar o GPS."
            return
        }

        consultandoGps = true
        localizacaoTexto = "Buscando a melhor leitura do GPS…"
        buscaGps = escopo.launch {
            try {
                val local = buscarMelhorGps(gerenciador)
                if (local == null) {
                    localizacaoTexto = "Sem sinal de GPS. Tente em um local aberto ou simule um ponto no emulador."
                } else {
                    latitude = local.latitude
                    longitude = local.longitude
                    val precisao = kotlin.math.ceil(local.accuracy.toDouble()).toInt()
                    localizacaoTexto = String.format(
                        Locale.forLanguageTag("pt-BR"),
                        "Latitude: %.6f\nLongitude: %.6f\nRaio de precisão estimado: %d m",
                        local.latitude,
                        local.longitude,
                        precisao
                    )
                    if (precisao > 15) {
                        localizacaoTexto += "\nPrecisão reduzida. Tente novamente em um local aberto."
                    }
                }
            } catch (_: SecurityException) {
                localizacaoTexto = "A localização precisa foi bloqueada pelo Android."
            } catch (_: IllegalArgumentException) {
                localizacaoTexto = "O GPS está indisponível neste aparelho."
            } finally {
                consultandoGps = false
            }
        }
    }

    val pedirPermissao = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        atualizarGps()
    }

    fun solicitarGps() {
        if (temLocalizacaoPrecisa()) {
            atualizarGps()
        } else {
            latitude = null
            longitude = null
            mensagemMapa = ""
            pedirPermissao.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

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

    LaunchedEffect(tela) {
        if (tela != "tutor" && consultandoGps) {
            buscaGps?.cancel()
            localizacaoTexto = "Consulta de GPS cancelada."
        }
    }

    fun abrirLembretes() {
        origemLembretes = tela
        tela = "lembretes"
    }

    fun voltar() {
        mecanismoVoz?.stop()
        tela = when (tela) {
            "recursos" -> "morador"
            "lembretes" -> origemLembretes
            else -> "inicio"
        }
    }

    BackHandler(enabled = tela != "inicio") {
        voltar()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Fundo)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
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
                            "Escolha como deseja acessar o app.",
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

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                Text("🔊", fontSize = 44.sp)
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
                                        registrarAcao(if (it) "Assistente de voz ativado." else "Assistente de voz desativado.")
                                        if (!it) mecanismoVoz?.stop()
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Button(
                            onClick = { tela = "recursos" },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Azul)
                        ) {
                            IconeAcao("📱", "RECURSOS DO APARELHO")
                        }

                        Spacer(Modifier.height(20.dp))

                        Button(
                            onClick = ::abrirLembretes,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Verde)
                        ) {
                            IconeAcao("🔔", "LEMBRETES DA CASA")
                        }

                        Spacer(Modifier.height(16.dp))

                        RotinasDaCasa(ultimaRotina) { rotina ->
                            if (rotina == RotinaCasa.SAIR) confirmarSaida = true
                            else executarRotina(rotina)
                        }

                        Spacer(Modifier.height(20.dp))

                        ControleGeracaoSolar(
                            simulando = solarSimulada,
                            alternar = {
                                solarSimulada = !solarSimulada
                                registrarAcao(
                                    if (solarSimulada) "Simulação solar iniciada."
                                    else "Simulação solar encerrada."
                                )
                                falar(
                                    if (solarSimulada) "Geração solar simulada: 2,4 quilowatts."
                                    else "Simulação de geração solar encerrada."
                                )
                            }
                        )

                        Spacer(Modifier.height(20.dp))

                        dispositivosPorSecao.forEach { (secao, dispositivos) ->
                            Text(secao, fontWeight = FontWeight.Bold, color = TextoEscuro)
                            Spacer(Modifier.height(12.dp))

                            dispositivos.forEach { dispositivo ->
                                val ajuste = dispositivo.ajuste
                                val valor = ajustes[dispositivo] ?: ajuste?.inicial
                                val janela = ajuste == AjusteDispositivo.ABERTURA_JANELA
                                val ativo = if (janela) (valor ?: 0) > 0 else estados[dispositivo] == true
                                val acao = dispositivo.acao
                                ControleAcessivel(
                                    dispositivo = dispositivo,
                                    ativo = ativo,
                                    valorAjuste = valor,
                                    ajustar = { ajustes[dispositivo] = it },
                                    registrarAjuste = { novoValor ->
                                        val descricao = when (ajuste) {
                                            AjusteDispositivo.TEMPERATURA -> "temperatura de $novoValor °C"
                                            AjusteDispositivo.INTENSIDADE -> "brilho de $novoValor%"
                                            AjusteDispositivo.ABERTURA_JANELA -> "abertura de $novoValor%"
                                            null -> null
                                        }
                                        if (descricao != null) {
                                            registrarAcao("${dispositivo.nome}: $descricao.")
                                        }
                                    },
                                    ouvir = {
                                        falar(
                                            "Este controle permite ${acao.ativar.lowercase()} ou " +
                                                "${acao.desativar.lowercase()}. ${dispositivo.nome}."
                                        )
                                    },
                                    acionar = { novoEstado ->
                                        if (janela) {
                                            ajustes[dispositivo] = if (novoEstado) 100 else 0
                                        } else {
                                            estados[dispositivo] = novoEstado
                                        }
                                        val comando = if (novoEstado) acao.ativar else acao.desativar
                                        registrarAcao("${dispositivo.nome}: $comando.")
                                        falar("$comando. ${dispositivo.nome}. Controle demonstrativo.")
                                    }
                                )
                            }
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
                            IconeAcao("🔊", "OUVIR INSTRUÇÕES DE EMERGÊNCIA")
                        }

                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = { confirmarSamu = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 82.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Vermelho
                            )
                        ) {
                            IconeAcao("☎", "EMERGÊNCIA GRAVE\nLIGAR SAMU 192")
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
                        registrarAcao("SOS demonstrativo acionado.")
                        falar(
                            "Pedido de ajuda registrado em demonstração."
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .heightIn(min = 76.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Vermelho
                    )
                ) {
                    IconeAcao("🆘", "SOS — PRECISO DE AJUDA")
                }

                Text(
                    "SOS DEMONSTRATIVO",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    color = Color.DarkGray
                )
            }

            "recursos" -> {
                Box(Modifier.weight(1f)) {
                    RecursosAparelho(::registrarAcao)
                }
            }

            "lembretes" -> {
                Box(Modifier.weight(1f)) {
                    TelaLembretes { descricao ->
                        if (origemLembretes == "morador") registrarAcao(descricao)
                    }
                }
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
                                    onClick = {
                                        if (temAcessoRedeLocal()) verificarServidor()
                                        else pedirRedeLocal.launch(PERMISSAO_REDE_LOCAL)
                                    },
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
                                    "📍 LOCALIZAÇÃO POR GPS",
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
                                        "Evento de demonstração."
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

                        Button(
                            onClick = ::abrirLembretes,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Verde)
                        ) {
                            IconeAcao("🔔", "CONFIGURAR LEMBRETES")
                        }

                        Spacer(Modifier.height(12.dp))

                        HistoricoDaCasa(historico.eventos)
                    }
                }
            }
        }

        if (tela != "inicio") {
            OutlinedButton(
                onClick = {
                    voltar()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .heightIn(min = 54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    when {
                        tela == "lembretes" && origemLembretes == "tutor" -> "← VOLTAR À CENTRAL DO TUTOR"
                        tela == "recursos" || tela == "lembretes" -> "← VOLTAR À MINHA CASA"
                        else -> "← VOLTAR À TELA INICIAL"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (confirmarSaida) {
        AlertDialog(
            onDismissRequest = { confirmarSaida = false },
            title = { Text("Executar Sair de casa?") },
            text = { Text(RotinaCasa.SAIR.descricao) },
            confirmButton = {
                Button(onClick = {
                    confirmarSaida = false
                    executarRotina(RotinaCasa.SAIR)
                }) {
                    Text("EXECUTAR ROTINA")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmarSaida = false }) {
                    Text("CANCELAR")
                }
            }
        )
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
                            registrarAcao("Discador do SAMU aberto.")
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
                    stringResource(R.string.app_name),
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
            stringResource(R.string.app_tagline),
            color = Color.White,
            fontSize = 13.sp
        )
    }
}

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
            .fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = corFinal
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 116.dp)
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
                .sizeIn(minWidth = 62.dp, minHeight = 62.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(icone, fontSize = 28.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            nome,
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
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

@Composable
private fun ControleGeracaoSolar(simulando: Boolean, alternar: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IconeAcao("☀️", "Placas solares")
            Text(
                if (simulando) "Potência simulada: 2,4 kW" else "Simulação desligada",
                fontSize = 19.sp,
                color = TextoEscuro
            )
            BotaoDispositivo(
                if (simulando) "ENCERRAR SIMULAÇÃO" else "SIMULAR GERAÇÃO",
                if (simulando) Verde else Azul,
                acionar = alternar
            )
        }
    }
}

@Composable
private fun ControleAcessivel(
    dispositivo: Dispositivo,
    ativo: Boolean,
    valorAjuste: Int?,
    ajustar: (Int) -> Unit,
    registrarAjuste: (Int) -> Unit,
    ouvir: () -> Unit,
    acionar: (Boolean) -> Unit
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
                Text(dispositivo.icone, fontSize = 48.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        dispositivo.nome,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextoEscuro
                    )
                    Text(
                        if (dispositivo.ajuste == AjusteDispositivo.ABERTURA_JANELA) {
                            when (valorAjuste) {
                                0 -> "Fechada (simulado)"
                                100 -> "Aberta (simulado)"
                                else -> "Parcialmente aberta (simulado)"
                            }
                        } else {
                            "${if (ativo) dispositivo.acao.ativo else dispositivo.acao.inativo} (simulado)"
                        },
                        fontSize = 13.sp,
                        color = if (ativo) Verde else Color.Gray
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            OutlinedButton(
                onClick = ouvir,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                IconeAcao("🔊", "OUVIR FUNÇÃO")
            }

            Spacer(Modifier.height(8.dp))

            if (dispositivo.acao == AcaoDispositivo.CORTINA) {
                BotaoDispositivo("↑ SUBIR", Azul, enabled = !ativo) { acionar(true) }
                Spacer(Modifier.height(8.dp))
                BotaoDispositivo("↓ DESCER", Azul, enabled = ativo) { acionar(false) }
            } else {
                val acao = dispositivo.acao
                val comando = if (ativo) acao.desativar else acao.ativar
                BotaoDispositivo(comando.uppercase(), if (ativo) Verde else Azul) {
                    acionar(!ativo)
                }
            }

            if (valorAjuste != null) {
                Spacer(Modifier.height(16.dp))
                when (dispositivo.ajuste) {
                    AjusteDispositivo.TEMPERATURA -> ControleTemperatura(valorAjuste) {
                        ajustar(it)
                        registrarAjuste(it)
                    }
                    AjusteDispositivo.INTENSIDADE -> ControlePercentual("Brilho", valorAjuste, 10, ajustar, registrarAjuste)
                    AjusteDispositivo.ABERTURA_JANELA -> ControlePercentual("Abertura", valorAjuste, 0, ajustar, registrarAjuste)
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun BotaoDispositivo(
    titulo: String,
    cor: Color,
    enabled: Boolean = true,
    acionar: () -> Unit
) {
    Button(
        onClick = acionar,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = cor)
    ) {
        Text(titulo, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ControleTemperatura(temperatura: Int, ajustar: (Int) -> Unit) {
    Text(
        "Temperatura: ${temperatura} °C",
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold,
        color = TextoEscuro
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { ajustar(temperatura - 1) },
            enabled = temperatura > 16,
            modifier = Modifier.weight(1f).heightIn(min = 54.dp)
        ) {
            Text("− 1 °C", fontSize = 18.sp)
        }
        OutlinedButton(
            onClick = { ajustar(temperatura + 1) },
            enabled = temperatura < 30,
            modifier = Modifier.weight(1f).heightIn(min = 54.dp)
        ) {
            Text("+ 1 °C", fontSize = 18.sp)
        }
    }
}

@Composable
private fun ControlePercentual(
    titulo: String,
    percentual: Int,
    minimo: Int,
    ajustar: (Int) -> Unit,
    registrarAjuste: (Int) -> Unit
) {
    var percentualInicial by remember { mutableStateOf<Int?>(null) }
    var percentualFinal by remember { mutableIntStateOf(percentual) }
    Text(
        "$titulo: $percentual%",
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold,
        color = TextoEscuro
    )
    Slider(
        value = percentual.toFloat(),
        onValueChange = {
            if (percentualInicial == null) percentualInicial = percentual
            percentualFinal = it.roundToInt()
            ajustar(percentualFinal)
        },
        onValueChangeFinished = {
            val inicial = percentualInicial
            if (inicial != null && inicial != percentualFinal) registrarAjuste(percentualFinal)
            percentualInicial = null
        },
        valueRange = minimo.toFloat()..100f,
        steps = (100 - minimo) / 10 - 1,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    )
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
