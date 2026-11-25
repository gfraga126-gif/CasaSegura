# Tutor em Casa

*Mais autonomia para a pessoa idosa, mais facilidade para quem cuida.*

O Tutor em Casa é um projeto acadêmico voltado à autonomia de pessoas idosas em casa. Ele reúne botões grandes, instruções por voz, lembretes e rotinas para facilitar tarefas do dia a dia. A Central do Tutor permite consultar, no mesmo aparelho, o histórico das ações registradas no app.

O protótipo foi feito em Kotlin com Jetpack Compose e abre direto na escolha entre **Pessoa Assistida** e **Familiar / Tutor**.

## Por que retiramos o login e a senha

Nesta versão de entrega, retiramos o login e a senha para que o professor possa instalar o app, escolher um perfil e testar os recursos locais sem criar conta ou configurar um servidor. Isso também reduz as etapas de entrada para a pessoa idosa e para quem cuida dela.

Os lembretes e o histórico ficam no próprio aparelho. Como os avisos entre dois celulares dependiam das contas, esse recurso ficou para uma versão futura. Também pretendemos avaliar o reconhecimento facial no aparelho para proteger o acesso às funções do tutor.

## O que dá para fazer no app

- **Pessoa Assistida** — controlar portas, janelas, cortina, iluminação e ar-condicionado em modo demonstrativo. Cada janela tem ajuste de abertura, cada luz tem dimmer e cada aparelho de ar tem ajuste de temperatura. A tela também tem simulação solar e as rotinas Boa noite, Cheguei em casa e Sair de casa.
- **Familiar / Tutor** — consultar a localização do próprio aparelho, conferir a conexão com o servidor e ver os 50 eventos mais recentes no histórico local.
- **Lembretes da casa** — configurar avisos diários para café, almoço, jantar e remédio. Cada aviso tem horário e chave de ativação próprios.

O SOS gera um registro demonstrativo no aparelho. O atalho do SAMU abre o discador com o 192 e a pessoa decide se faz a chamada. Os controles da casa ainda não estão ligados a um ESP32.

## Como executar

1. Abra a pasta do projeto no Android Studio e aguarde a sincronização do Gradle. O projeto usa o SDK Android 37.
2. Selecione um emulador ou celular Android e execute a configuração **app**. Na tela inicial, escolha um dos dois perfis. O servidor é necessário apenas para o teste de conexão pela internet. Para demonstrar a localização, deixe o GPS ligado e conceda a localização precisa.
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

## Melhorias futuras

- Avaliar o reconhecimento facial no aparelho para proteger o acesso às funções do tutor.
- Desenvolver o acompanhamento à distância com autorização da pessoa assistida e integração com dispositivos reais.
