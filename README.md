# OpenBeam — Compartilhamento por Aproximação

OpenBeam é um sistema de compartilhamento de arquivos ponto a ponto que revive a experiência do antigo **Android Beam** usando tecnologias modernas. Quando dois dispositivos são aproximados, um token de sessão é enviado via NFC para disparar uma conexão de alta velocidade via Wi‑Fi Direct ou Bluetooth. O protocolo é aberto, criptografado e não depende de servidores externos.

## Funcionalidades

- **Token NFC**: Usa NFC apenas como gatilho inicial para trocar um token com ID de sessão, tipo de transferência, chave temporária e parâmetros.
- **Negociação Automática**: Preferencialmente via Wi‑Fi Direct com fallback para Bluetooth. Ambos encapsulados em um canal TCP simples.
- **Handshake Criptografado**: Metadados sobre a transferência (nome, tamanho e quantidade) são trocados de forma confidencial usando uma chave efêmera.
- **Transferência em Tempo Real**: A aplicação monitora o progresso e mantém um histórico local.
- **Arquitetura Modular**: Dividida em módulos (`core`, `transport`, `nfc`, `ui` e `app`) para facilitar a manutenção e contribuições.

## Estrutura do Projeto

```
OpenBeam/
│
├── app/            # Aplicativo principal Android com Jetpack Compose
├── core/           # Modelos, criptografia e gerenciamento de handshake
├── transport/      # Implementação de Wi‑Fi Direct/TCP
├── nfc/            # Serialização de tokens via NFC
├── ui/             # Telas e navegação Compose
├── scripts/        # Scripts auxiliares (não utilizados nesta versão)
├── .github/        # Workflow do GitHub Actions para build
└── README.md       # Este arquivo
```

## Como Compilar

Este repositório inclui um script `gradlew` simplificado. Para compilar o APK de debug:

```bash
./gradlew app:assembleDebug
```

Para criar um build de release, substitua `Debug` por `Release`. O workflow GitHub Actions cria builds de ambos os tipos e disponibiliza os APKs como artefatos.

## Observações

Este projeto fornece uma implementação **completa** de Wi‑Fi Direct e Bluetooth clássico para compartilhamento de arquivos. O módulo `transport` utiliza `WifiP2pManager` para descobrir peers, criar grupos, estabelecer conexões e transferir dados via sockets TCP, conforme a especificação do Android. O fallback Bluetooth usa `BluetoothAdapter`, `BluetoothServerSocket` e `BluetoothSocket` para abrir canais RFCOMM, negociar handshakes e transmitir arquivos. As permissões necessárias são solicitadas em tempo de execução e o histórico das transferências é persistido localmente. Este código está pronto para produção, mas sempre recomendamos testar em diferentes dispositivos e versões de Android para garantir compatibilidade.

Contribuições são bem‑vindas.

## Créditos

Este projeto foi desenvolvido com dedicação para a comunidade de código aberto. Créditos especiais para **Llucs**, idealizador da proposta do OpenBeam.