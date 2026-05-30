# OpenBeam — Compartilhamento por Aproximação

OpenBeam é um sistema de compartilhamento de arquivos ponto a ponto que revive a experiência do antigo **Android Beam** usando tecnologias modernas. Quando dois dispositivos são aproximados, um token de sessão é enviado via NFC para disparar uma conexão de alta velocidade via Wi‑Fi Direct ou Bluetooth. O protocolo é aberto, criptografado e não depende de servidores externos.

## Funcionalidades

- **Token NFC**: Usa NFC apenas como gatilho inicial para trocar um token com ID de sessão, tipo de transferência, chave temporária e parâmetros.
- **Negociação Automática**: Preferencialmente via Wi‑Fi Direct com fallback para Bluetooth. Ambos encapsulados em um canal TCP simples.
- **Handshake Criptografado**: Metadados sobre a transferência (nome, tamanho e quantidade) são trocados de forma confidencial usando uma chave efêmera (AES-256-GCM via Tink).
- **Transferência em Tempo Real**: A aplicação monitora o progresso e mantém um histórico local com Room.
- **Arquitetura Modular**: Dividida em módulos (`core`, `transport`, `nfc`, `ui` e `app`) para facilitar a manutenção e contribuições.

## Estrutura do Projeto

```
OpenBeam/
│
├── app/            # Aplicativo principal Android com Jetpack Compose
├── core/           # Modelos, criptografia (Tink) e gerenciamento de handshake
├── transport/      # Implementação Wi‑Fi Direct e Bluetooth (TCP + RFCOMM)
├── nfc/            # Serialização e leitura de tokens via NFC
├── ui/             # Telas e navegação Compose + Navigation
├── .github/        # Workflow do GitHub Actions para build
└── README.md
```

## Requisitos

- **JDK 17**
- **Android SDK 34**
- **Gradle 8.5** (incluso via Gradle Wrapper)

## Como Compilar

```bash
./gradlew app:assembleDebug
```

Para Release:

```bash
./gradlew app:assembleRelease
```

O APK será gerado em `app/build/outputs/apk/`.

## CI/CD

O repositório inclui um workflow do **GitHub Actions** que compila o projeto e gera os APKs como artefatos. O workflow pode ser acionado manualmente com a opção Debug ou Release.

## Permissões

O app solicita em tempo de execução as permissões necessárias para cada funcionalidade:

- NFC
- Bluetooth (SCAN, CONNECT, ADVERTISE)
- Localização (para descoberta Wi‑Fi Direct)
- Wi‑Fi (STATE, CHANGE)

## Licença

Este projeto está licenciado sob a licença MIT. Consulte o arquivo [LICENSE](LICENSE) para mais detalhes.
