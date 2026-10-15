
package com.casasegura.automacao

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Azul = Color(0xFF1565C0)
private val Verde = Color(0xFF00796B)
private val Vermelho = Color(0xFFC62828)
private val VermelhoEscuro = Color(0xFF8B0000)
private val Fundo = Color(0xFFF2F6FA)

private val nomesDispositivos = listOf(
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
    var tela by remember { mutableStateOf("inicio") }

    val estados = remember {
        mutableStateListOf(
            false, false, false,
            false, false, false
        )
    }

    var sosSimulado by remember {
        mutableStateOf(false)
    }

    BackHandler(enabled = tela != "inicio") {
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
                .padding(20.dp)
        ) {
            Text(
                text = "CASA SEGURA",
                fontSize = 27.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Automação Residencial Assistiva",
                color = Color.White,
                fontSize = 14.sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "MODO DEMONSTRAÇÃO | ESP32 desconectado",
                color = Color.White,
                fontSize = 11.sp
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (tela) {

                "inicio" -> {
                    TelaInicial(
                        abrirMorador = {
                            tela = "morador"
                        },
                        abrirTutor = {
                            tela = "tutor"
                        }
                    )
                }

                "morador" -> {
                    TelaMorador(
                        estados = estados,
                        voltar = {
                            tela = "inicio"
                        },
                        solicitarAjuda = {
                            sosSimulado = true
                        }
                    )
                }

                "tutor" -> {
                    TelaTutor(
                        sosSimulado = sosSimulado,
                        voltar = {
                            tela = "inicio"
                        }
                    )
                }
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
        text = "BEM-VINDO AO CASA SEGURA",
        fontSize = 23.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(12.dp))

    Text(
        text = "Escolha como deseja acessar",
        fontSize = 18.sp,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(35.dp))

    Button(
        onClick = abrirMorador,
        modifier = Modifier
            .fillMaxWidth()
            .height(115.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Azul
        )
    ) {
        Text(
            text = "PESSOA ASSISTIDA\n\nCONTROLAR MINHA CASA",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }

    Spacer(Modifier.height(22.dp))

    Button(
        onClick = abrirTutor,
        modifier = Modifier
            .fillMaxWidth()
            .height(115.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Verde
        )
    ) {
        Text(
            text = "FAMILIAR / TUTOR\n\nACOMPANHAR RESIDÊNCIA",
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }

    Spacer(Modifier.height(30.dp))

    Text(
        text = "Versão de demonstração.\n" +
                "Login e acesso remoto serão adicionados depois.",
        fontSize = 14.sp,
        textAlign = TextAlign.Center
    )
}

@Composable
fun TelaMorador(
    estados: androidx.compose.runtime.snapshots.SnapshotStateList<Boolean>,
    voltar: () -> Unit,
    solicitarAjuda: () -> Unit
) {
    val contexto = LocalContext.current

    var mensagem by remember {
        mutableStateOf("")
    }

    var confirmarSamu by remember {
        mutableStateOf(false)
    }

    Text(
        text = "MINHA CASA",
        fontSize = 29.sp,
        fontWeight = FontWeight.Bold
    )

    Spacer(Modifier.height(8.dp))

    Text(
        text = "O que deseja fazer?",
        fontSize = 21.sp
    )

    Spacer(Modifier.height(20.dp))

    nomesDispositivos.forEachIndexed { indice, nome ->

        val aberto = estados[indice]

        Button(
            onClick = {
                estados[indice] = !aberto

                val acao = if (aberto) {
                    "Fechar"
                } else {
                    "Abrir"
                }

                mensagem = "$acao $nome (simulado)"
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 82.dp)
                .padding(bottom = 8.dp),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(14.dp)
        ) {
            Text(
                text = (
                        if (aberto) "FECHAR " else "ABRIR "
                        ) + nome.uppercase(),
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }

    if (mensagem.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))

        Text(
            text = mensagem,
            fontSize = 16.sp,
            color = Azul,
            textAlign = TextAlign.Center
        )
    }

    Spacer(Modifier.height(25.dp))

    Button(
        onClick = {
            solicitarAjuda()

            mensagem = "SOS SIMULADO: o tutor " +
                    "não recebeu uma notificação real."
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(115.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Vermelho
        )
    ) {
        Text(
            text = "SOS\nPRECISO DE AJUDA",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }

    Spacer(Modifier.height(16.dp))

    Button(
        onClick = {
            confirmarSamu = true
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = VermelhoEscuro
        )
    ) {
        Text(
            text = "EMERGÊNCIA GRAVE\nLIGAR SAMU 192",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }

    Spacer(Modifier.height(15.dp))

    Text(
        text = "Em risco imediato, ligue 192.\n" +
                "O SOS do aplicativo ainda é uma simulação.",
        fontSize = 14.sp,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(22.dp))

    BotaoVoltar(voltar)

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
                    "O aplicativo abrirá o discador " +
                            "com o número 192. Confirme a " +
                            "ligação no aplicativo de telefone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmarSamu = false

                        val intent = Intent(
                            Intent.ACTION_DIAL,
                            Uri.parse("tel:192")
                        )

                        try {
                            contexto.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            mensagem = "Este dispositivo " +
                                    "não possui discador disponível."
                        } catch (e: SecurityException) {
                            mensagem = "Não foi possível " +
                                    "abrir o discador."
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
}

@Composable
fun TelaTutor(
    sosSimulado: Boolean,
    voltar: () -> Unit
) {
    Text(
        text = "CENTRAL DO TUTOR",
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(10.dp))

    Text(
        text = "Acompanhamento da pessoa assistida",
        fontSize = 16.sp,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(25.dp))

    if (sosSimulado) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 15.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFFFCDD2)
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "SOS SIMULADO",
                    fontSize = 22.sp,
                    color = VermelhoEscuro,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "A pessoa assistida pressionou " +
                            "o botão SOS nesta sessão.\n" +
                            "Nenhuma notificação real foi enviada.",
                    fontSize = 16.sp
                )
            }
        }
    }

    CartaoInformacao(
        "CONEXÃO DA CASA",
        "ESP32 desconectado"
    )

    CartaoInformacao(
        "ATIVIDADE DO MORADOR",
        "Aguardando sensores de presença"
    )

    CartaoInformacao(
        "PORTAS E JANELAS",
        "Estado real ainda indisponível"
    )

    CartaoInformacao(
        "SENSOR DE CHUVA",
        "Aguardando comunicação"
    )

    CartaoInformacao(
        "ALERTAS DE SEGURANÇA",
        "Notificações ainda não configuradas"
    )

    CartaoInformacao(
        "ACESSO REMOTO",
        "Integração com internet será adicionada"
    )

    Spacer(Modifier.height(20.dp))

    BotaoVoltar(voltar)
}

@Composable
fun CartaoInformacao(
    titulo: String,
    descricao: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(15.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = titulo,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = descricao,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun BotaoVoltar(aoVoltar: () -> Unit) {
    OutlinedButton(
        onClick = aoVoltar,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 55.dp)
    ) {
        Text(
            text = "VOLTAR À TELA PRINCIPAL",
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
    }
}
