# CasaSegura

Projeto individual da disciplina de desenvolvimento Android. O CasaSegura é um protótipo de automação residencial assistiva feito em Kotlin com Jetpack Compose. A ideia é facilitar tarefas da casa com botões grandes, instruções de voz e rotinas com um toque.

## O que dá para fazer no app

- **Pessoa Assistida** — controlar portas, janelas, cortina, iluminação e ar-condicionado em modo demonstrativo. Cada janela tem ajuste de abertura, cada luz tem dimmer e cada aparelho de ar tem ajuste de temperatura. A tela também tem simulação solar e as rotinas Boa noite, Cheguei em casa e Sair de casa.
- **Familiar / Tutor** — consultar a localização do próprio aparelho, conferir a conexão com o servidor e ver os 50 eventos mais recentes no histórico local.
- **Lembretes da casa** — configurar avisos diários para café, almoço, jantar e remédio. Cada aviso tem horário e chave de ativação próprios.

O SOS gera um registro demonstrativo e, com duas contas vinculadas, envia um aviso de atividade ao outro celular. O atalho do SAMU abre o discador com o 192 e a pessoa decide se faz a chamada. Os controles da casa ainda não estão ligados a um ESP32.

## Como executar

1. Abra a pasta do projeto no Android Studio e aguarde a sincronização do Gradle. O projeto usa o SDK Android 37.
2. Selecione um emulador ou celular Android e execute a configuração **app**. Para explorar os recursos sem servidor, toque em **Usar demonstração local**. Para demonstrar a localização, deixe o GPS ligado e conceda a localização precisa.
3. Para testar a conexão no emulador, inicie o servidor no computador:

   ```bash
   cd backend
   npm ci
   node server.js
   ```

4. No app, abra **Familiar / Tutor** e toque em **Atualizar conexão**. O emulador consulta `http://10.0.2.2:3000/api/status`. Esse endereço aponta para o computador somente no emulador.

## Roteiro de demonstração

| Recurso | Como testar |
| --- | --- |
| Câmera | Em **Pessoa Assistida → Recursos do aparelho**, toque em **Tirar foto** e confirme para ver a imagem no app. |
| Microfone | Na mesma tela, permita o acesso, grave um recado, pare a gravação e toque em **Ouvir recado**. |
| Giroscópio | Gire o celular para observar a mudança no indicador. No emulador, use os sensores virtuais. |
| GPS | Em **Familiar / Tutor**, toque em **Atualizar localização** e permita a localização precisa. O app mostra as coordenadas e o raio de precisão estimado. No emulador, envie um ponto pelos controles de localização durante a consulta. |
| Internet | Com o servidor em execução, toque em **Atualizar conexão** para consultar o status. |
| Rotinas | Execute **Boa noite** em Pessoa Assistida e confira o registro no **Histórico da casa**, na Central do Tutor. |

A foto e o recado ficam no aparelho enquanto a tela de recursos estiver aberta. O recado tem duração máxima de um minuto. O histórico fica salvo no aparelho e usa a data e a hora do Android.

Para testar com a webcam do notebook, pare o emulador e abra **Device Manager → Edit → Additional settings**. Em **Camera → Rear**, escolha **Webcam0**, salve e inicie o dispositivo virtual novamente. A webcam depende do acesso concedido pelo sistema do computador.

## Como testar os lembretes

Abra **Pessoa Assistida → Lembretes da casa** ou **Familiar / Tutor → Configurar lembretes**. Permita as notificações, escolha o horário e ative a chave do aviso. Os horários sugeridos são 08:00 para café, 12:00 para almoço, 19:00 para jantar e 20:00 para remédio. Todos começam desativados.

Ao ativar um lembrete, o Android mostra uma confirmação com o horário escolhido. O botão **Testar notificação** envia um aviso de demonstração na hora. Os lembretes ativos são salvos no aparelho e reagendados após uma reinicialização. O Android pode atrasar a entrega dos avisos agendados, especialmente em economia de bateria.

## Como vincular dois celulares

1. Instale o CasaSegura nos dois aparelhos e informe o mesmo endereço **HTTPS** do servidor nos dois. O endereço padrão `http://10.0.2.2:3000` serve para o emulador acessar o computador.
2. Toque em **Cadastrar tutor** em um celular e **Cadastrar pessoa assistida** no outro. Crie um usuário e uma senha próprios para cada conta. Ambas podem acessar os dois perfis do app.
3. Após entrar, abra **Minha conta e avisos entre celulares**. Gere um código em um aparelho e digite-o no outro. O código vale por dez minutos e só pode ser usado uma vez.
4. Autorize os avisos do Android nos dois aparelhos. Execute uma ação em **Pessoa Assistida**. No outro aparelho, confira a notificação ou abra **Avisos recebidos**.

O app compartilha descrições das ações, como abertura de janela, ajuste da luz, uso de uma rotina, alteração de lembrete ou SOS demonstrativo. Fotos, gravações e coordenadas ficam no aparelho. Com o app aberto, os avisos são consultados a cada cinco segundos. Em segundo plano, o Android agenda a consulta com um intervalo mínimo de 15 minutos e pode atrasá-la conforme a rede e a bateria.

Para servir dois celulares fora do emulador, execute o Node.js em um servidor acessível pelos dois aparelhos, atrás de HTTPS com certificado válido. O processo usa a porta definida em `PORT` ou 3000. Use `CASASEGURA_DATA_FILE` para apontar para um arquivo de dados em um volume persistente e privado do servidor. Esse arquivo guarda as contas e as atividades e deve ficar fora do Git. O servidor demonstrativo foi feito para funcionar em uma única instância.
