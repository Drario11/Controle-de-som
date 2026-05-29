# Preparacao para Google Play

Este documento resume os ajustes feitos e os pontos que ainda exigem preenchimento no Play Console.

## Ajustes aplicados no projeto

- Removido `VolumeGestureAccessibilityService` do manifesto e do codigo.
- Removido o arquivo `accessibility_service_config.xml`.
- Removidos textos com linguagem de uso oculto, invisivel ou discreto.
- Adicionado pedido claro da permissao de notificacao no Android 13+.
- Adicionada politica de privacidade em `PRIVACY_POLICY.md`.
- Adicionado rascunho de Data Safety em `docs/data-safety.md`.

## Declaracao de Foreground Service

O app usa um servico em primeiro plano para manter a area lateral de gesto disponivel enquanto o usuario escolhe usar o controle de volume.

Texto base para justificativa:

> O aplicativo fornece um controle lateral de volume iniciado pelo usuario. O servico em primeiro plano mantem a area de gesto ativa sobre outros apps e mostra uma notificacao persistente enquanto o recurso esta em uso. O usuario pode parar o controle pelo app.

Material recomendado para revisao:

- video curto mostrando o usuario ativando o servico;
- permissao de sobreposicao sendo concedida;
- controle lateral funcionando;
- notificacao do servico ativo;
- botao para parar o servico.

## Permissao de sobreposicao

Texto base para revisao:

> A permissao de exibir sobre outros apps e necessaria para mostrar a area lateral de gesto e o controle manual de volume sobre o app que o usuario estiver usando. O app nao captura texto, senhas, imagens ou conteudo de outros apps.

## Checklist antes de enviar

- Definir nome do desenvolvedor e email de suporte.
- Hospedar a politica de privacidade em uma URL publica.
- Preencher Data Safety com base em `docs/data-safety.md`.
- Enviar primeiro para teste interno ou teste fechado.
- Testar em Samsung, Motorola, Xiaomi/MIUI, Realme/Oppo/ColorOS e Android 13+.
- Confirmar que o app funciona com notificacoes permitidas e com economia de bateria desativada para o app.

## Observacoes de risco

- Overlays podem ser bloqueados em telas sensiveis, como apps bancarios, telas de senha e alguns jogos.
- Fabricantes com economia agressiva de bateria podem interromper o servico se o usuario restringir o app.
- Se recursos de rede, analytics ou anuncios forem adicionados no futuro, atualize `PRIVACY_POLICY.md` e `docs/data-safety.md`.

