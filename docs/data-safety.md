# Data Safety - Rascunho para Play Console

Use este arquivo como base para preencher a secao "Seguranca dos dados" no Play Console.

## Coleta e transmissao

Estado recomendado para a versao atual:

- O app nao transmite dados para servidores externos.
- O app nao possui permissao `INTERNET` no manifesto.
- O app armazena localmente historico tecnico de gestos de volume, se o usuario usar o recurso de historico.

Se o Play Console perguntar por "coleta", considere que dados armazenados apenas localmente e nao enviados para fora do dispositivo normalmente nao entram como coleta remota. Confirme a resposta final com a definicao atual do Play Console antes de publicar.

## Dados locais

Dados gravados no aparelho:

- timestamp do gesto;
- direcao do ajuste;
- volume inicial;
- volume final;
- quantidade alterada.

Uso: historico local e diagnostico funcional do app.

## Permissoes declaradas

- `SYSTEM_ALERT_WINDOW`: exibir a area lateral e o controle de volume sobre outros apps.
- `POST_NOTIFICATIONS`: mostrar notificacao do servico ativo no Android 13+.
- `FOREGROUND_SERVICE`: manter o servico ativo enquanto o usuario usa o recurso.
- `FOREGROUND_SERVICE_SPECIAL_USE`: declarar o caso especial do controle lateral persistente.
- `MODIFY_AUDIO_SETTINGS`: alterar volume de midia.
- `VIBRATE`: retorno tactil.

## Respostas sugeridas

- Compartilha dados com terceiros: Nao.
- Usa dados para publicidade: Nao.
- Usa dados para analytics remoto: Nao.
- Permite exclusao: Sim, por limpeza de historico/dados do app ou desinstalacao.

