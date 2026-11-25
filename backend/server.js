
const express = require("express");

const app = express();
const PORT = Number(process.env.PORT || 3000);

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
        notificacaoEnviada: false,
        horario: new Date().toISOString()
    };

    console.log("SOS demonstrativo:", evento);

    res.json({
        sucesso: true,
        mensagem: "SOS registrado apenas em demonstracao",
        notificacaoEnviada: false,
        evento
    });
});

app.use((req, res) => {
    res.status(404).json({
        erro: "Rota nao encontrada"
    });
});

app.listen(PORT, "0.0.0.0", () => {
    console.log(
        `CasaSegura funcionando na porta ${PORT}`
    );
});
