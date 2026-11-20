package com.casasegura.automacao

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val formatoHorario = DateTimeFormatter.ofPattern("HH:mm")

@Composable
internal fun TelaLembretes(registrarAcao: (String) -> Unit) {
    val contexto = LocalContext.current
    val agenda = remember(contexto) { AgendaLembretes(contexto) }
    val ciclo = LocalLifecycleOwner.current.lifecycle
    val lembretes = remember(agenda) {
        mutableStateListOf<Lembrete>().apply { addAll(TipoLembrete.entries.map(agenda::carregar)) }
    }
    var podeNotificar by remember(agenda) { mutableStateOf(agenda.podeNotificar()) }
    var mensagem by remember { mutableStateOf<String?>(null) }
    val pedirPermissao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        podeNotificar = agenda.podeNotificar()
        agenda.enviarAtivacoesPendentes()
        mensagem = if (podeNotificar) "Notificações liberadas." else "Notificações bloqueadas no Android."
    }

    DisposableEffect(ciclo, agenda) {
        val observador = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_RESUME) {
                podeNotificar = agenda.podeNotificar()
                agenda.enviarAtivacoesPendentes()
                if (podeNotificar && mensagem == "Notificações bloqueadas no Android.") mensagem = null
            }
        }
        ciclo.addObserver(observador)
        onDispose { ciclo.removeObserver(observador) }
    }

    fun atualizar(indice: Int, lembrete: Lembrete) {
        val anterior = lembretes[indice]
        if (anterior == lembrete) return
        agenda.salvar(lembrete)
        lembretes[indice] = lembrete
        val tipo = lembrete.tipo.titulo
        val horario = lembrete.horario.format(formatoHorario)
        val ativadoAgora = lembrete.ativo && !anterior.ativo
        val avisoExibido = !ativadoAgora || agenda.notificarAtivacao(lembrete)
        val acao = when {
            ativadoAgora -> "Lembrete ativado: $tipo às $horario."
            anterior.ativo && !lembrete.ativo -> "Lembrete desativado: $tipo."
            else -> "Horário alterado: $tipo às $horario."
        }
        registrarAcao(acao)
        podeNotificar = agenda.podeNotificar()
        mensagem = if (!avisoExibido) {
            "Lembrete ativado. Permita as notificações para receber o aviso."
        } else if (lembrete.ativo) {
            "${lembrete.tipo.titulo} agendado para ${lembrete.horario.format(formatoHorario)}."
        } else {
            "${lembrete.tipo.titulo}: ${lembrete.horario.format(formatoHorario)} (desativado)."
        }
    }

    ConteudoRolavel {
        TituloSecao("Lembretes da casa", "Configure o horário dos avisos diários.")
        Spacer(Modifier.height(12.dp))

        if (!podeNotificar) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Autorize as notificações para receber os avisos.")
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pedirPermissao.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            abrirConfiguracaoNotificacoes(contexto)
                        }
                    }) {
                        Text("Permitir notificações")
                    }
                    TextButton(onClick = { abrirConfiguracaoNotificacoes(contexto) }) {
                        Text("Configurações do Android")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        mensagem?.let {
            Text(
                it,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))
        }

        lembretes.forEachIndexed { indice, lembrete ->
            CartaoLembrete(
                lembrete = lembrete,
                alternar = { atualizar(indice, lembrete.copy(ativo = it)) },
                editar = {
                    TimePickerDialog(
                        contexto,
                        { _, hora, minuto -> atualizar(indice, lembrete.copy(horario = LocalTime.of(hora, minuto))) },
                        lembrete.horario.hour,
                        lembrete.horario.minute,
                        true
                    ).apply {
                        setTitle(lembrete.tipo.titulo)
                        show()
                    }
                }
            )
            Spacer(Modifier.height(12.dp))
        }

        OutlinedButton(
            onClick = {
                mensagem = if (agenda.notificar(TipoLembrete.CAFE, teste = true)) {
                    "Notificação de teste enviada."
                } else {
                    "Permita as notificações do CasaSegura."
                }
            },
            enabled = podeNotificar,
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
        ) {
            IconeAcao("🔔", "Testar notificação")
        }
        Spacer(Modifier.height(8.dp))
        Text("O Android pode atrasar os avisos, especialmente em economia de bateria.")
    }
}

private fun abrirConfiguracaoNotificacoes(contexto: android.content.Context) {
    contexto.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, contexto.packageName)
    )
}

@Composable
private fun CartaoLembrete(lembrete: Lembrete, alternar: (Boolean) -> Unit, editar: () -> Unit) {
    val titulo = lembrete.tipo.titulo
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (lembrete.ativo) MaterialTheme.colorScheme.primaryContainer else Color.White,
            contentColor = if (lembrete.ativo) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { IconeAcao(lembrete.tipo.icone, titulo) }
                Switch(
                    checked = lembrete.ativo,
                    onCheckedChange = alternar,
                    modifier = Modifier.semantics { contentDescription = titulo }
                )
            }
            Text(
                lembrete.horario.format(formatoHorario),
                fontSize = 32.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(onClick = editar, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("Alterar horário")
            }
        }
    }
}