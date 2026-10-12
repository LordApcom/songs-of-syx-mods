# Species Customs Gate (reescrita para V71.44 sobre Polyphon)

Cada portao (sala `GATEHOUSE`) ganha o seu proprio filtro de **especie x classe (cidadao / escravo)** e uma **seta**
que pode ser invertida. Nenhuma classe do jogo e substituida: tudo e feito com ganchos do Polyphon.

## Como usar
- **Clique direito** num tile de portao (ou tecla **F** com o mouse sobre o portao) abre o painel do filtro.
  O clique esquerdo continua trancando/destrancando, como no jogo base.
- Painel: grade de especies (linha de cidadaos, linha de escravos), atalhos **Allow all / Citizens only / Slaves only /
  Block all**, botao **Invert arrow** e **Apply**.
- A dica flutuante do portao mostra: `Everyone`, `NONE allowed` ou `Allowed (x/y): lista`, mais a direcao da seta.
- Setas amarelas permanentes aparecem sobre todos os tiles de portao.
- Tudo fica gravado no save (por portao: ancora, seta invertida, bits). Salvo sem o mod = portoes abertos; salvo com o mod e
  carregado sem ele = o jogo ignora o bloco.

## Regras de passagem
- Quem **e permitido** passa nos dois sentidos.
- Quem **nao e permitido** so entra pelo lado de tras da seta, em linha reta, e sai pela frente. Contra a seta ou pelas
  laterais, o portao age como parede para essa pessoa.
- **Hostis, rebeldes, turistas e soldados** sempre passam (soldados: escolha minha, senao a defesa quebra).
  Nobres, criancas e prisioneiros (sem especie/classe filtravel) tambem passam.
- Dentro do corredor do portao o movimento e livre; quem aparece ja dentro (teletransporte, filtro mudou) nunca e preso.

## Aviso: seta para dentro
Quem nao e permitido e esta do lado de dentro de uma seta que aponta para dentro **nao sai**, nem para comer ou dormir
(planos de trabalho cruzam a seta). Decisao de desenho: a seta segura de verdade, entao ponha comida e casas do lado
de dentro de um cercado assim.

## Como os presos foram evitados (por desenho)
1. **Decide na hora de escolher o plano**: o buscador de servicos/consumo nao oferece um alvo que o humanoide alcanca mas do
   qual nao consegue voltar (lazer). Planos de trabalho podem cruzar com a seta.
2. **Alcance em tres estados** (`CONNECTED / DISCONNECTED / BUDGET_EXHAUSTED`): so "DISCONNECTED provado" bloqueia; sem
   orcamento no tick = permite e tenta de novo depois. So conta como desconectado o que e alcancavel *sem* portoes e nao
   *com* eles (regioes isoladas por outros motivos nao sao culpa do portao).
3. Mapa de regioes por especie: o mapa e inundado tratando os portoes restritos como paredes; cada portao vira aresta de
   sentido unico entre regioes (a seta). Reaproveitado pela API.
4. Estado por sessao: `InstanceState` (por jogo e por humanoide), nada estatico que sobreviva entre partidas. Limpeza ao
   remover o humanoide e ao trocar de plano.
5. Rede de seguranca minima: 8 batidas num portao em ~4 s -> o plano e interrompido, o alvo e esquecido por ~30 s nas
   buscas de servico e, na 3a vez em ~2 min, o humanoide perde o emprego.
6. Buscas SFINDER "qualquer tile" (estoque, recursos, trabalhos) ficam **sem filtro** de proposito: filtrar fazia os
   carregadores pararem.

## API para outros mods
`lordapcom.gate.GateApi.canReach(Humanoid h, int tx, int ty)` -> `true` se o mod esta inativo, h e hostil/turista, a especie
e permitida em todos os portoes, o alvo e alcancavel respeitando filtros e setas, ou o orcamento acabou.
`false` apenas quando provado desconectado. (O Workplace Species Filter ja chama por reflexao.)

## Compatibilidade
- Requer **Polyphon** (>= 0.2.0). Ganchos em `ResolverTile.collides`, `Humanoid.collideTile`/`removeAction`,
  `AIModules.getNextPlan`, `SFinderRoomService.getReservable`. `getNextPlan` e `getReservable` tambem sao usados pelo Workplace
  Species Filter; o Polyphon encadeia os ganchos, e o meu so adiciona restricoes (nunca libera o que o outro bloqueou).
- Nao troca nenhuma classe do jogo.

## Nao implementado
- **Roteamento em torno do portao**: o pathfinder do jogo e por tile (e roda numa thread propria, com grafo de
  componentes), sem ponto de gancho que conheca o humanoide. Quem ainda assim escolhe um caminho por um portao proibido
  e barrado pela colisao e cai na rede de seguranca. Consequencia: a regra "entrada e saida so em passo reto" vale na
  colisao (so entra pela camada de entrada, vindo de tras da seta), nao no calculo de rota.
- **Carrocas de carga e caravanas** nao sao `Humanoid` (sao half-entities) e nao passam pela colisao filtrada: cruzam
  livremente nos dois sentidos, mais permissivo que "so com a seta".
- **Plano de casa** (`SFinderHome.findHome`): nao filtrado. Recusar a casa desatribui a moradia, pior que o problema.
- Escolhas de servico feitas na *retomada* de um plano (fora de `getNextPlan`) nao sao filtradas, so as de plano novo.
- `SFinderWater` e `SFinderIndoors` nao sao filtrados.
- Painel de diagnostico por cidadao.

## Creditos
Requer o Polyphon (VizardAlpha).
