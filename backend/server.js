
const express = require("express");

const app = express();
const PORT = 3000;
const HOST = "127.0.0.1";

app.use(express.json());

// Tela inicial da API
app.get("/", (req, res) => {
    res.json({
        projeto: "CasaSegura",
        status: "online",
        modo: "demonstracao"
    });
const express = require("express");

const app = express();
const PORT = 3000;

// Somente para desenvolvimento local.
// Não expor esta API à internet.
const HOST = "0.0.0.0";

app.use(express.json({ limit: "10kb" }));

app.get("/", (req, res) => {
    res.json({
        projeto: "CasaSegura",
        status: "online",
        modo: "demonstracao"
    });
});

app.get("/api/status", (req, res) => {
    res.json({
        servidor: "online",
        esp32: "desconectado",
        monitoramento: "aguardando sensores",
        notificacoes: "nao configuradas",
        horario: new Date().toISOString()
    });
});

app.post("/api/sos", (req, res) => {
    const evento = {
        id: Date.now().toString(),
        tipo: "SOS_DEMONSTRACAO",
        data: new Date().toISOString(),
        notificacaoEnviada: false
    };

    console.log("SOS de demonstracao:", evento);

    res.json({
        sucesso: true,
        mensagem: "SOS registrado somente em demonstracao",
        notificacaoEnviada: false,
        evento
    });
});

app.use((req, res) => {
    res.status(404).json({
        erro: "Rota nao encontrada"
    });
});

app.listen(PORT, HOST, () => {
    console.log("==============================");
    console.log("CASA SEGURA - BACK-END");
    console.log("==============================");
    console.log(`Servidor na porta ${PORT}`);
    console.log("Modo: desenvolvimento local");
    console.log("ESP32: desconectado");
    console.log("==============================");
});
