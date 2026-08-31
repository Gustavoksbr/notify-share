# Notify Share

https://github.com/Gustavoksbr/notify-share

Compartilhe as notificações do seu Android com pessoas que você escolher — e só
o que você escolher.

O celular avisa você de várias coisas: uma mensagem do José no WhatsApp, a
bateria em 20%, o Wi-Fi que caiu. O Notify Share deixa você repassar esses
avisos para alguém específico, com regras finas sobre **o quê**, **de quem** e
**para quem**.

Android apenas. O iOS não expõe nenhuma API equivalente ao
`NotificationListenerService`, então o lado que compartilha é tecnicamente
impossível lá.

---

## Casos de uso

**Filho acompanhando pai ou mãe idosos.**
A mãe compartilha as notificações do WhatsApp e os alertas de bateria. O filho
percebe quando ela não responde há horas, ou quando o celular dela está prestes
a desligar. Ela vê exatamente o que está compartilhando e desliga quando quiser.

**Delegação de atendimento.**
O dono de um negócio compartilha só o WhatsApp Business com a assistente, e só
as mensagens de clientes — nada do WhatsApp pessoal, que continua invisível.

**Casal dividindo a logística.**
Notificações de entrega, banco e portaria vão para os dois, sem ninguém
precisar tirar print e reenviar.

**Plantão e on-call.**
Quem está de sobreaviso compartilha os alertas do sistema de monitoramento com
quem vai render o turno, sem dar acesso à ferramenta inteira.

**Dois aparelhos, uma pessoa.**
O celular de trabalho repassa para o pessoal. Aqui as duas pontas são suas —
esse é o caso device-to-device, e ele é cidadão de primeira classe no produto.

**Acessibilidade.**
Alguém com deficiência visual pode ter um cuidador recebendo os avisos
importantes em paralelo.

---

## Como funciona

```
   APARELHO DE ORIGEM                SERVIDOR              APARELHO DE DESTINO

  NotificationListener  ─┐
  Bateria / Wi-Fi / etc ─┼─► Event ─► Regras ─► HTTPS ─► roteia ─► FCM ─► notificação local
  (EventSource)         ─┘           (local)              + histórico
```

Três decisões moldam tudo:

**As regras rodam no aparelho de origem.** O parsing da notificação e o filtro
"José sim, Maria não" acontecem antes de qualquer coisa sair do celular. O
servidor nunca precisa ler o conteúdo para fazer o trabalho dele — ele roteia.

**O filtro por remetente é genérico.** WhatsApp, Telegram, Signal, Messenger e
Discord usam `Notification.MessagingStyle`, que carrega um objeto `Person` por
mensagem. Não existe engenharia reversa por aplicativo: qualquer app que
notifique funciona, e o filtro por pessoa funciona onde o app usa o padrão de
mensagem.

**O FCM é o transporte oficial.** WebSocket não sobrevive ao Doze — o sistema
suspende a rede do processo quando a tela apaga. O WebSocket existe só como
atalho enquanto o app está em primeiro plano; toda entrega passa pelo FCM.

---

## Estado atual

| Etapa | O quê | Status |
|-------|-------|--------|
| 1 | Backend: cadastro, login, JWT com refresh rotacionado | **funcionando** |
| 2 | Backend: amigos, busca por nickname, grants, DM | a fazer |
| 3 | Mobile: Compose, tema, login, armazenamento de token | **funcionando** |
| 4 | Mobile: captura de eventos, regras, FCM | a fazer |
| 5 | Sobrevivência em segundo plano (watchdog, heartbeat) | a fazer |

Os mockups das telas e as decisões de arquitetura estão fora de `app/`, na raiz
do repositório: `DECISOES.md` e `design/`.

---

## Rodando o backend

Precisa de Java 21 e um Postgres local.

```bash
createdb notifyshare          # ou: psql -U postgres -c "create database notifyshare"
cd backend
./gradlew bootRun
```

Sobe em `http://localhost:8080`. O Flyway cria o schema sozinho na primeira
execução.

A documentação interativa fica em **http://localhost:8080/swagger** — dá para
exercitar a API inteira por ali, inclusive as rotas autenticadas (o botão
*Authorize* no topo aceita o `accessToken`). Em produção, `SWAGGER_ENABLED=false`
tira a UI e o `/v3/api-docs` do ar.

Configuração vem de variáveis de ambiente, com defaults de desenvolvimento em
`application.yml` — as chaves estão documentadas em `backend/.env.example`.
Em produção, `JWT_SECRET` é obrigatório e precisa de pelo menos 32 bytes.

### Endpoints

| Método | Rota | Autenticado | O quê |
|--------|------|-------------|-------|
| POST | `/auth/register` | não | Cria conta e já devolve os tokens |
| POST | `/auth/login` | não | Aceita nickname **ou** e-mail em `identifier` |
| POST | `/auth/refresh` | não | Rotaciona o refresh token |
| POST | `/auth/logout` | não | Revoga um refresh token |
| POST | `/auth/logout-all` | sim | Encerra todas as sessões |
| GET | `/me` | sim | Perfil do próprio usuário |

Erros saem sempre no mesmo formato, e o cliente decide pelo `code`, nunca pela
mensagem:

```json
{ "code": "nickname_taken", "message": "Esse nickname ja esta em uso", "field": "nickname" }
```

### Sobre a autenticação

`nickname` é o identificador público. `email` é obrigatório e único, mas privado
e **não verificado** nesta versão — ele existe para limitar uma conta por
endereço, não para provar que o endereço é real. Não há recuperação de senha
ainda. O porquê disso está no ADR-001 em `DECISOES.md`.

Access token dura 15 minutos. O refresh dura 30 dias, é opaco (não é JWT, para
poder ser revogado), o servidor guarda apenas o SHA-256 dele, e cada uso emite
um novo e invalida o anterior. Se um token já rotacionado reaparecer, é sinal de
vazamento: toda a família de tokens daquele login é revogada de uma vez.

---

## Estrutura

```
app/
├── backend/    Kotlin + Spring Boot 4 + Postgres + Flyway
│   └── src/main/kotlin/com/notifyshare/
│       ├── auth/      domain · application · adapter
│       └── shared/    config · web
└── mobile/     Kotlin + Jetpack Compose + AGP 9
    └── app/src/main/java/com/notifyshare/
        ├── data/      local (tokens cifrados) · remote (Retrofit)
        └── ui/        auth · home · theme
```

O backend é hexagonal por pacote, organizado por feature, num módulo Gradle só.
Existem portas onde a troca de implementação é real e datada — envio de e-mail,
push, armazenamento de mídia. Não existe porta em volta de `JpaRepository`:
`JpaRepository` já é uma porta, e envolvê-la em outra interface acrescentaria
arquivos sem desacoplar nada.

O mobile segue Clean-lite por feature (`data` / `ui`), com MVVM e `StateFlow`.
As decisões e as armadilhas do AGP 9 estão no ADR-005 em `DECISOES.md`.

---

## Rodando o app

Precisa do Android SDK 36 e de um aparelho com depuração USB.

```bash
cd mobile
./gradlew :app:installDebug
adb reverse tcp:8080 tcp:8080
```

O `adb reverse` é o que faz o `localhost:8080` do aparelho chegar no backend
rodando no PC. Sem ele o app não acha o servidor.

Em aparelhos Xiaomi/HyperOS é preciso ligar **"Instalar via USB"** nas opções de
desenvolvedor, além da depuração USB — senão o `adb install` falha com
`INSTALL_FAILED_USER_RESTRICTED`.

Os tokens ficam cifrados com AES/GCM usando uma chave do Android Keystore, no
DataStore. A tela mostrada é decidida pela sessão, não pela navegação: qualquer
coisa que limpe os tokens devolve o usuário ao login sozinha.
