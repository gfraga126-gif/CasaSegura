const crypto = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");

const file = process.env.CASASEGURA_DATA_FILE || path.join(__dirname, ".casa-segura-data.json");
const lifetime = 10 * 60 * 1000;
const maxEvents = 200;
const sessionsLifetime = 30 * 24 * 60 * 60 * 1000;
const attempts = new Map();

function readData() {
    try {
        return JSON.parse(fs.readFileSync(file, "utf8"));
    } catch (error) {
        if (error.code === "ENOENT") return { accounts: [], links: [] };
        throw error;
    }
}

function saveData(data) {
    const temporary = file + ".tmp";
    fs.writeFileSync(temporary, JSON.stringify(data), { mode: 0o600 });
    fs.renameSync(temporary, file);
}

function digest(token) {
    return crypto.createHash("sha256").update(token).digest("hex");
}

function issueSession(account, res, data) {
    const token = crypto.randomBytes(32).toString("hex");
    account.session = digest(token);
    account.expires = Date.now() + sessionsLifetime;
    saveData(data);
    return res.json({ token, usuario: account.usuario, perfil: account.perfil });
}

function limit(req, res, next) {
    const now = Date.now();
    for (const [key, entry] of attempts) {
        if (entry.until <= now) attempts.delete(key);
    }
    const key = req.ip;
    const entry = attempts.get(key) || { until: now + 60_000, count: 0 };
    entry.count++;
    attempts.set(key, entry);
    if (entry.count > 12) return res.status(429).json({ erro: "Aguarde um minuto e tente novamente." });
    next();
}

module.exports = function installNotifications(app) {
    app.post("/api/contas", limit, (req, res) => {
        const usuario = String(req.body?.usuario ?? "").trim().toLowerCase();
        const senha = req.body?.senha;
        if (!/^[a-z0-9_.-]{3,32}$/.test(usuario) || typeof senha !== "string" || senha.length < 8 || senha.length > 72) {
            return res.status(400).json({ erro: "Use um usuário válido e uma senha de 8 a 72 caracteres." });
        }
        const data = readData();
        if (data.accounts.some(item => item.usuario === usuario)) {
            return res.status(409).json({ erro: "Esse nome de usuário já está em uso." });
        }
        const perfil = req.body?.perfil;
        if (perfil !== "tutor" && perfil !== "assistida") {
            return res.status(400).json({ erro: "Escolha o perfil da conta." });
        }
        const salt = crypto.randomBytes(16).toString("hex");
        const hash = crypto.scryptSync(senha, salt, 64).toString("hex");
        const account = { id: crypto.randomUUID(), usuario, perfil, salt, hash };
        data.accounts.push(account);
        return issueSession(account, res, data);
    });

    app.post("/api/sessoes", limit, (req, res) => {
        const usuario = String(req.body?.usuario ?? "").trim().toLowerCase();
        const senha = req.body?.senha;
        const data = readData();
        const account = data.accounts.find(item => item.usuario === usuario);
        if (typeof senha !== "string" || senha.length < 8 || senha.length > 72) {
            return res.status(401).json({ erro: "Usuário ou senha incorretos." });
        }
        const provided = crypto.scryptSync(senha, account?.salt || "conta-inexistente", 64);
        if (!account || !crypto.timingSafeEqual(provided, Buffer.from(account.hash, "hex"))) {
            return res.status(401).json({ erro: "Usuário ou senha incorretos." });
        }
        return issueSession(account, res, data);
    });

    function authenticated(req, res, next) {
        const token = req.get("authorization")?.match(/^Bearer ([0-9a-f]{64})$/i)?.[1];
        const data = readData();
        const session = token && digest(token);
        const account = session && data.accounts.find(item => item.session === session && item.expires > Date.now());
        if (!account) return res.status(401).json({ erro: "Entre novamente para continuar." });
        req.casa = { data, account, link: data.links.find(item => item.members.includes(account.id)) };
        res.set("Cache-Control", "no-store");
        next();
    }

    app.delete("/api/sessoes", authenticated, (req, res) => {
        const { data, account } = req.casa;
        delete account.session;
        delete account.expires;
        saveData(data);
        res.json({ ok: true });
    });

    app.get("/api/vinculo", authenticated, (req, res) => {
        const { link } = req.casa;
        const result = { vinculado: link?.members.length === 2, vinculo: link?.id };
        if (link?.members.length === 1 && link.expires > Date.now()) result.codigo = link.code;
        res.json(result);
    });

    app.post("/api/vinculo/codigo", authenticated, (req, res) => {
        const { data, account, link } = req.casa;
        if (link?.members.length === 2) return res.status(409).json({ erro: "As contas já estão vinculadas." });
        const pending = link || { id: crypto.randomUUID(), members: [account.id], events: [], nextId: 1 };
        pending.code = crypto.randomBytes(8).toString("hex").toUpperCase();
        pending.expires = Date.now() + lifetime;
        if (!link) data.links.push(pending);
        saveData(data);
        res.json({ codigo: pending.code, vinculado: false, vinculo: pending.id });
    });

    app.post("/api/vinculo/entrar", limit, authenticated, (req, res) => {
        const { data, account, link } = req.casa;
        const code = String(req.body?.codigo ?? "").trim().toUpperCase();
        const target = data.links.find(item => item.code === code && item.expires > Date.now());
        if (!target || target === link) return res.status(400).json({ erro: "Código inválido ou expirado." });
        if (link?.members.length === 2) return res.status(409).json({ erro: "Desvincule a conta atual antes." });
        if (link) data.links = data.links.filter(item => item !== link);
        target.members.push(account.id);
        delete target.code;
        delete target.expires;
        saveData(data);
        res.json({ vinculado: true, vinculo: target.id });
    });

    app.delete("/api/vinculo", authenticated, (req, res) => {
        const { data, link } = req.casa;
        if (link) {
            data.links = data.links.filter(item => item !== link);
            saveData(data);
        }
        res.json({ ok: true });
    });

    app.post("/api/atividades", authenticated, (req, res) => {
        const { data, account, link } = req.casa;
        if (link?.members.length !== 2) return res.status(409).json({ erro: "Vincule os dois aparelhos primeiro." });
        if (req.body?.vinculo !== link.id) return res.status(409).json({ erro: "Vínculo alterado." });
        const key = req.body?.chave;
        const text = req.body?.descricao;
        if (typeof key !== "string" || !/^[0-9a-f-]{36}$/.test(key) ||
            typeof text !== "string" || !text.trim() || text.length > 240) {
            return res.status(400).json({ erro: "Atividade inválida." });
        }
        if (!link.events.some(event => event.author === account.id && event.key === key)) {
            link.events.push({ id: link.nextId++, author: account.id, key, text, time: Date.now() });
            link.events = link.events.slice(-maxEvents);
            saveData(data);
        }
        res.json({ ok: true });
    });

    app.get("/api/atividades", authenticated, (req, res) => {
        const { account, link } = req.casa;
        const after = req.query.vinculo === link?.id ? Number(req.query.depois ?? 0) : 0;
        const events = link?.events || [];
        res.json({
            vinculado: link?.members.length === 2,
            vinculo: link?.id,
            codigo: link?.members.length === 1 && link.expires > Date.now() ? link.code : "",
            eventos: events.filter(event => event.id > after && event.author !== account.id)
                .map(event => ({ id: event.id, descricao: event.text, instante: event.time }))
        });
    });
};