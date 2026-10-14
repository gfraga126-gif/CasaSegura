
package com.casasegura.automacao

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val AzulCasa = Color(0xFF1565C0)
val FundoCasa = Color(0xFFF0F4F8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CasaSegura()
            }
        }
    }
}

@Composable
fun CasaSegura() {
    var tela by remember { mutableIntStateOf(0) }
    var mensagem by remember {
        mutableStateOf("Sistema em modo demonstração")
    }

    val nomes = listOf(
        "Porta principal",
        "Portão",
        "Janela da sala",
        "Janela do quarto 1",
        "Janela do quarto 2",
        "Cortina"
    )

    val estados = remember {
        mutableStateListOf(false, false, false, false, false, false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FundoCasa)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AzulCasa)
                .padding(20.dp)
        ) {
            Text(
                "CASA SEGURA",
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                "Automação Residencial Assistiva",
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "● ESP32 desconectado | Demonstração",
                color = Color.White,
                fontSize = 12.sp
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            when (tela) {
                0 -> {
                    Text(
                        "Minha residência",
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Controle os equipamentos da casa.")
                    Spacer(Modifier.height(16.dp))

                    nomes.forEachIndexed { indice, nome ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        nome,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        if (estados[indice])
                                            "Aberto (simulado)"
                                        else
                                            "Fechado (simulado)",
                                        fontSize = 13.sp
                                    )
                                }
                                Button(
                                    onClick = {
                                        estados[indice] = !estados[indice]
                                        mensagem = "$nome: comando simulado"
                                    }
                                ) {
                                    Text(
                                        if (estados[indice])
                                            "Fechar"
                                        else
                                            "Abrir"
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        mensagem,
                        color = AzulCasa,
                        fontWeight = FontWeight.Medium
                    )
                }

                1 -> {
                    Text(
                        "Monitoramento",
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(16.dp))

                    Informacao(
                        "Estado da conexão",
                        "ESP32 ainda não conectado"
                    )
                    Informacao(
                        "Monitoramento do morador",
                        "Aguardando sensores"
                    )
                    Informacao(
                        "Sensor de chuva",
                        "Aguardando conexão"
                    )
                    Informacao(
                        "Notificações",
                        "Ainda não configuradas"
                    )
                }

                2 -> {
                    Text(
                        "Central de emergência",
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Nesta área, o morador poderá pedir ajuda " +
                                "aos familiares e cuidadores."
                    )
                    Spacer(Modifier.height(30.dp))

                    Button(
                        onClick = {
                            mensagem =
                                "SOS SIMULADO! Nenhum alerta foi enviado."
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFC62828)
                        )
                    ) {
                        Text(
                            "PEDIR AJUDA\nSOS",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    Text(
                        mensagem,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Atenção: nesta versão o botão ainda " +
                                "não envia alertas reais."
                    )
                }
            }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Casa", "Monitorar", "SOS")
                .forEachIndexed { indice, nome ->
                    Button(
                        onClick = {
                            tela = indice
                            mensagem = "Sistema em modo demonstração"
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (tela == indice)
                                AzulCasa
                            else Color(0xFF607D8B)
                        )
                    ) {
                        Text(nome, fontSize = 13.sp)
                    }
                }
        }
    }
}

@Composable
fun Informacao(titulo: String, descricao: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                titulo,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Spacer(Modifier.height(5.dp))
            Text(descricao)
        }
    }
}
