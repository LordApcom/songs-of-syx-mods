# Workplace Species Filter (V71.44)

Mod de código para Songs of Syx. Dá controle **por edifício** sobre quem pode trabalhar nele e quem pode usar o serviço dele.

## O que faz
Dois filtros independentes por edifício, cada um com alternadores por **espécie** e por **classe** (cidadão / escravo):

1. **Trabalhadores**: quem pode assumir emprego no edifício (qualquer local de trabalho).
2. **Usuários**: quem pode usar o serviço (banhos, templos, santuários, palcos, arenas, cantinas, tavernas, hospitais, etc.; qualquer sala que seja fornecedora de serviço).

É um **bloqueio duro**: uma espécie/classe não permitida nunca é contratada naquele edifício, nem usa aquele serviço (o serviço some das buscas dela). A preferência suave nativa (grupos de trabalho no painel de prioridades) continua funcionando e convive com o filtro.

## Como usar
1. Na barra inferior, clique no botao **Ferramentas** (engrenagem). O mod adiciona a entrada **Species filter** como mais uma celula da grade de ferramentas (depois da ultima ferramenta), com o mesmo estilo das demais.
2. Ao clicar, a ferramenta abre a ferramenta **Species filter**. O cabecalho laranja/azul no topo mostra o modo atual: **SETTING: WORKERS** (quem pode trabalhar) ou **SETTING: USERS** (quem pode usar o servico). Troque nos botoes Workers / Users; cada modo guarda a sua propria selecao.
3. Marque as especies/classes **permitidas** (botoes marcados = permitido; atalhos: todos, todos os cidadaos, todos os escravos, ninguem).
4. Clique ou arraste sobre os edificios. Seleção com todos marcados remove o filtro.
5. Enquanto a ferramenta esta ativa, **todo local de trabalho (martelo) e todo edificio de servico (coracao)** ganha um contorno nas bordas e uma placa no centro: **?** = sem filtro (todos); icone de classe = so cidadaos ou so escravos; icones de especie = so essas; icones riscados = todos **menos** esses; cancelar = ninguem. Contorno e martelo **laranja** = filtro de trabalhadores, **azul** e coracao = filtro de usuarios; o modo atual aparece forte e o outro esmaecido. Predios que sao as duas coisas mostram as duas placas empilhadas.

Ao restringir trabalhadores de um edifício, quem já trabalha lá e não é permitido é demitido na hora.

## Persistência e compatibilidade
- Salvo junto do save. Saves antigos ou sem o mod não têm dados: nenhum filtro (todos permitidos).
- O filtro fica preso à instância do prédio; ao demolir o prédio o filtro some. Não sobrevive a reconstruir.
- Mod sempre ativo (não aparece na lista de scripts do novo jogo). Remover o mod de um save que o usava gera o aviso normal do jogo de script ausente.
- Requer **Polyphon** (VizardAlpha). Não substitui classes do jogo, só injeta nos pontos listados abaixo. Pode conflitar com mods que mexam em contratação (`StatsWork`/`RoomEmployments`) ou na busca de serviço (`SFinderRoomService`, `AIModules`).
- Integração opcional com o mod SpeciesCustomsGate: se a classe `lordapcom.gate.GateApi` existir (checada uma vez por reflexão), a atribuição de trabalhador é recusada quando `GateApi.canReach(humanoid, x, y)` retornar falso. Sem o mod, nada acontece.

## Como funciona (resumo)
- Contratação: o setter central de emprego (`StatsWork$Work.set`) recusa a atribuição; o sorteio de instâncias (`RoomEmployments$RaceGroup.setWork`) pula edifícios que não aceitam o humanoide; `EmployerSimple.employ` (guardas, treinadores, universidade, asilo) reporta falha se foi recusado.
- Alocação: se nenhuma instância ativa de um tipo de edifício aceita uma espécie, a prioridade dela ali é reportada como 0, e o alocador do jogo não reserva vagas para ela.
- Serviço: `AIModules.getNextPlan` marca quem está escolhendo o plano; `SFinderRoomService.getReservable` esconde tiles de serviço de salas que excluem essa pessoa.

## Limitações conhecidas
- Na tela de prioridades de trabalho, uma espécie totalmente bloqueada num tipo de edifício aparece com prioridade 0; clicar nos botões de prioridade ali nesse estado grava a partir de 0.
- Troca (swap) de emprego entre dois trabalhadores pode deixar uma sala 1 pessoa acima da meta por alguns instantes se um dos lados for recusado; o jogo corrige sozinho.
- Quem já está a caminho do serviço quando o filtro de usuários muda termina a viagem.
- Se vagas de uma espécie ficam reservadas pelo alocador em um tipo de edifício onde só alguns prédios aceitam, parte pode ficar vazia até a próxima rodada de alocação.

## Não implementado
- Nada do conceito foi omitido. A entrada fica no menu Ferramentas da barra inferior (sem tecla de atalho propria nem busca de ferramentas).

Requer o Polyphon (VizardAlpha).
