package com.casasegura.automacao

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun TelaEntradaCasa(acesso: AcessoCasa, demonstracao: () -> Unit) {
    var cadastro by rememberSaveable { mutableStateOf<PerfilConta?>(null) }
    var servidor by rememberSaveable { mutableStateOf(AcessoCasa.SERVIDOR_EMULADOR) }
    var usuario by rememberSaveable { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    val acao = rememberAcaoConta()

    ConteudoRolavel {
        val titulo = cadastro?.let { "Cadastrar ${it.titulo.lowercase()}" } ?: "Entrar no CasaSegura"
        TituloSecao(titulo, "Use sua própria conta para vincular os dois celulares.")
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = servidor, onValueChange = { servidor = it },
            label = { Text("Endereço do servidor") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = usuario, onValueChange = { usuario = it },
            label = { Text("Usuário") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = senha, onValueChange = { senha = it },
            label = { Text(if (cadastro == null) "Senha" else "Senha (mínimo de 8 caracteres)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        BotaoConta(if (cadastro == null) "Entrar" else "Criar conta", !acao.ocupado && usuario.isNotBlank() && senha.isNotBlank()) {
            acao.executar { acesso.autenticar(servidor, usuario, senha, cadastro) }
        }
        acao.mensagem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (cadastro == null) {
            BotaoConta("Cadastrar tutor", !acao.ocupado) { cadastro = PerfilConta.TUTOR; acao.limpar() }
            BotaoConta("Cadastrar pessoa assistida", !acao.ocupado) { cadastro = PerfilConta.ASSISTIDA; acao.limpar() }
            TextButton(onClick = demonstracao, enabled = !acao.ocupado) { Text("Usar demonstração local") }
        } else {
            TextButton(onClick = { cadastro = null; acao.limpar() }, enabled = !acao.ocupado) {
                Text("Voltar para entrar")
            }
        }
    }
}

@Composable
internal fun TelaVinculoCasa(acesso: AcessoCasa, conta: SessaoCasa) {
    val contexto = LocalContext.current
    val vinculo by acesso.vinculo.collectAsState()
    val recebidos by acesso.recebidos.collectAsState()
    val acao = rememberAcaoConta()
    var codigo by rememberSaveable { mutableStateOf("") }
    var confirmar by remember { mutableStateOf(false) }
    val permissao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    ConteudoRolavel {
        TituloSecao("Avisos entre celulares", "${conta.usuario} · ${conta.perfil.titulo}")
        Spacer(Modifier.height(12.dp))
        Text("Ao vincular as contas, o outro celular recebe descrições das ações feitas em Pessoa Assistida.")
        Spacer(Modifier.height(12.dp))
        if (vinculo.ativo) {
            Text("Contas vinculadas", style = MaterialTheme.typography.titleMedium)
            BotaoConta("Desvincular contas", !acao.ocupado) { confirmar = true }
        } else {
            BotaoConta("Gerar código de vínculo", !acao.ocupado) {
                acao.executar(acesso::gerarCodigo)
            }
            if (vinculo.codigo.isNotEmpty()) {
                SelectionContainerCodigo(vinculo.codigo)
                Text("O código vale por 10 minutos e só pode ser usado uma vez.")
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = codigo, onValueChange = { codigo = it.take(16) },
                label = { Text("Código do outro celular") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            BotaoConta("Vincular", !acao.ocupado && codigo.trim().length == 16) {
                acao.executar { acesso.vincular(codigo); codigo = "" }
            }
        }

        BotaoConta("Permitir avisos do Android", !acao.ocupado) {
            if (Build.VERSION.SDK_INT >= 33 && !AgendaLembretes(contexto).podeNotificar()) {
                permissao.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                contexto.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, contexto.packageName)
                )
            }
        }
        acao.mensagem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(12.dp))
        if (vinculo.ativo) {
            Text("Com o app aberto, os avisos são consultados a cada poucos segundos. Em segundo plano, o Android pode atrasar a consulta.")
            Spacer(Modifier.height(12.dp))
            HistoricoDaCasa(recebidos, "Avisos recebidos", "Ações registradas no outro celular.")
        }
        TextButton(onClick = { acao.executar(acesso::sair) }, enabled = !acao.ocupado) { Text("Sair da conta") }
    }

    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("Desvincular os dois celulares?") },
            text = { Text("Os próximos avisos deixam de ser compartilhados.") },
            confirmButton = {
                TextButton(onClick = { confirmar = false; acao.executar(acesso::desvincular) }) { Text("Desvincular") }
            },
            dismissButton = {
                TextButton(onClick = { confirmar = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun SelectionContainerCodigo(codigo: String) {
    androidx.compose.foundation.text.selection.SelectionContainer {
        Text(codigo, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun BotaoConta(titulo: String, habilitado: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = habilitado,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 52.dp)
    ) { Text(titulo) }
}

private class AcaoConta(private val iniciar: (suspend () -> Unit) -> Unit) {
    var ocupado by mutableStateOf(false)
        private set
    var mensagem by mutableStateOf<String?>(null)
        private set

    fun limpar() {
        mensagem = null
    }

    fun executar(acao: suspend () -> Unit) {
        if (ocupado) return
        ocupado = true
        mensagem = null
        iniciar {
            try {
                acao()
            } catch (cancelado: CancellationException) {
                throw cancelado
            } catch (erro: AcessoCasa.RespostaServidor) {
                mensagem = erro.message
            } catch (erro: java.io.IOException) {
                mensagem = erro.message?.takeIf { it.startsWith("Informe") || it.startsWith("Use um endereço") }
                    ?: "Não foi possível falar com o servidor. Confira o endereço e a conexão."
            } catch (_: org.json.JSONException) {
                mensagem = "O servidor respondeu em um formato inesperado."
            } finally {
                ocupado = false
            }
        }
    }
}

@Composable
private fun rememberAcaoConta(): AcaoConta {
    val escopo = rememberCoroutineScope()
    return remember(escopo) { AcaoConta { acao -> escopo.launch { acao() } } }
}