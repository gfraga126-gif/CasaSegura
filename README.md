
# CasaSegura

## Automação Residencial Assistiva

O CasaSegura é um aplicativo Android desenvolvido em Kotlin,
utilizando Jetpack Compose.

O projeto surgiu da ideia de integrar uma casa automatizada
com ESP32 a um aplicativo acessível para idosos e pessoas
com mobilidade reduzida.

## Objetivo

Facilitar o acesso aos controles residenciais e oferecer
uma interface de acompanhamento para familiares e tutores.

## Funcionalidades

### Pessoa Assistida
- Interface com botões grandes e acessíveis
- Assistente de voz com TextToSpeech
- Controle demonstrativo da porta principal
- Controle demonstrativo do portão
- Controle demonstrativo das três janelas
- Controle demonstrativo da cortina
- Botão SOS demonstrativo
- Acesso ao discador do SAMU 192

### Familiar / Tutor
- Central de acompanhamento
- Consulta de conexão com o servidor
- Consulta de localização GPS com autorização
- Abertura da localização no Google Maps
- Visualização de informações demonstrativas da residência

## Interface

O aplicativo possui um layout com:
- Fundo claro
- Cabeçalho em degradê azul
- Botões grandes e arredondados
- Cartões individuais para os dispositivos
- Ícones de identificação
- Cores diferentes para ações e emergências

## Tecnologias utilizadas

- Kotlin
- Android Studio
- Jetpack Compose
- Android LocationManager
- TextToSpeech
- Node.js
- Express
- Git
- GitHub

## Servidor

O projeto possui um back-end demonstrativo em Node.js.

Para iniciar o servidor:

```bash
cd backend
node server.js
```

O emulador Android utiliza o endereço 10.0.2.2:3000
para acessar o servidor executado no computador.

## Limitações atuais

Os controles da casa ainda são simulados e não acionam
diretamente os equipamentos do ESP32.

O botão SOS não envia notificações reais.

A localização é consultada no próprio aparelho, com
autorização do usuário. Ainda não existe rastreamento remoto.

O projeto é um protótipo acadêmico e não substitui
um sistema real de emergência.

## Melhorias futuras

- Integração com ESP32
- Controle remoto dos dispositivos
- Login para familiares e tutores
- Notificações reais de emergência
- Compartilhamento autorizado de localização
- Monitoramento de sensores da residência

## Autor

Projeto acadêmico desenvolvido para estudo de
programação Android e automação residencial.
