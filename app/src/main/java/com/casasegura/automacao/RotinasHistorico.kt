package com.casasegura.automacao

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class RotinaCasa(val titulo: String, val descricao: String, val icone: String) {
    BOA_NOITE(
        "Boa noite",
        "Apaga sala e cozinha, fecha as janelas, desce a cortina e acende o quarto 1 a 20%.",
        "🌙"
    ),
    CHEGUEI(
        "Cheguei em casa",
        "Acende garagem e sala e liga o ar da sala na temperatura ajustada.",
        "🏠"
    ),
    SAIR(
        "Sair de casa",
        "Apaga as luzes internas, desliga os aparelhos de ar e fecha as janelas.",
        "🚪"
    )
}

@Composable
internal fun RotinasDaCasa(ultimaRotina: String?, executar: (RotinaCasa) -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Rotinas da casa", style = MaterialTheme.typography.titleLarge)
            Text(ultimaRotina ?: "As rotinas simulam os controles da casa.")
            RotinaCasa.entries.forEach { rotina ->
                Button(
                    onClick = { executar(rotina) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) {
                    IconeAcao(rotina.icone, rotina.titulo)
                }
                Text(rotina.descricao)
            }
        }
    }
}

internal data class EventoCasa(val instante: Long, val descricao: String)

internal class HistoricoCasa(contexto: Context) {
    private val preferencias = contexto.getSharedPreferences("historico_casa", Context.MODE_PRIVATE)
    private val registros = mutableStateListOf<EventoCasa>().apply { addAll(carregar()) }
    val eventos: List<EventoCasa> get() = registros

    fun registrar(descricao: String) {
        registros.add(0, EventoCasa(System.currentTimeMillis(), descricao))
        if (registros.size > LIMITE) registros.removeAt(registros.lastIndex)

        val json = JSONArray()
        registros.forEach { evento ->
            json.put(JSONObject().put("instante", evento.instante).put("descricao", evento.descricao))
        }
        preferencias.edit().putString("eventos", json.toString()).apply()
    }

    private fun carregar(): List<EventoCasa> {
        val salvo = preferencias.getString("eventos", null) ?: return emptyList()
        return try {
            val json = JSONArray(salvo)
            (0 until minOf(json.length(), LIMITE)).mapNotNull { indice ->
                val item = json.optJSONObject(indice) ?: return@mapNotNull null
                val instante = item.optLong("instante")
                val descricao = item.optString("descricao")
                if (instante > 0 && descricao.isNotBlank()) EventoCasa(instante, descricao) else null
            }
        } catch (_: JSONException) {
            emptyList()
        }
    }

    private companion object {
        const val LIMITE = 50
    }
}

@Composable
internal fun HistoricoDaCasa(
    eventos: List<EventoCasa>,
    titulo: String = "Histórico da casa",
    subtitulo: String = "Eventos simulados registrados neste aparelho."
) {
    val formato = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR"))
        .withZone(ZoneId.systemDefault())

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleLarge)
            Text(subtitulo)
            if (eventos.isEmpty()) {
                Text("Nenhum evento registrado.")
            }
            eventos.forEachIndexed { indice, evento ->
                if (indice > 0) HorizontalDivider()
                Text(formato.format(Instant.ofEpochMilli(evento.instante)), style = MaterialTheme.typography.labelLarge)
                Text(evento.descricao, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}