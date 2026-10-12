# Plan Ahead

Mod de código para Songs of Syx V71 (0.71.44). Requer o Polyphon.

## O que faz
- Todo edifício fica clicável no menu de construção, mesmo com a tecnologia ainda bloqueada.
- Você arrasta a área normalmente. Se a tecnologia já foi pesquisada, constrói como sempre.
- Se ainda está bloqueada, a construção nasce no Modo de Planejamento do jogo: reserva o espaço, sem trabalhadores nem recursos.
- O jogo recusa ativar o plano (ferramenta Ativar) enquanto a tecnologia estiver bloqueada. Depois da pesquisa, ative como qualquer plano.
- Vale também para edifícios de mods de dados: qualquer sala com requisito de tecnologia é tratada igual.
- Colar/copiar (Copiar sala, cópia em massa) e desenhos salvos (Ctrl+clique / seletor de blueprints) também aceitam salas bloqueadas; elas viram planos dormentes.
- Decorações que gastam recursos (estátuas etc.) são salas comuns e entram no mesmo fluxo.

## Como usar
Abra o menu de construção, escolha qualquer edifício (mesmo cinza no jogo original) e desenhe. Ele aparece como plano dormente. Quando a pesquisa terminar, use a ferramenta Ativar (ou o modo de planejamento desligado + Ativar) sobre o plano.

## Segurança em saves
Não guarda dados importantes no save (só um inteiro de versão). Pode remover o mod: planos viram construções dormentes normais. Se remover com planos de salas bloqueadas, ative-os só após a pesquisa (sem o mod nada impede a ativação antes).

## Compatibilidade
Usa mixins do Polyphon (sem substituir classes do jogo). Pontos: `Lockable.passes`, `CONSTRUCTION.pcreate`, `ConstructionInstance.jobToggle`, `StateManager.activate`.

## Não implementado
- Estradas, muros/estruturas e fortificações atrás de tecnologia: não são salas, são "jobs" com travas verificadas em vários lugares (cada tipo de job, `getPlacable`, textos de bloqueio). Não há um ponto único e limpo para liberar e depois bloquear a ativação.
- Salas gratuitas, ao ar livre e sem piso (construídas na hora pelo jogo, sem etapa de planejamento) continuam travadas: planejá-las as construiria instantaneamente.
- Paredes de um plano, ativadas individualmente pela ferramenta Ativar sobre a parede, podem ser construídas (a sala em si continua bloqueada).
- O bug do jogo de colar planos dentro de montanha (hospital, enfermaria etc.) não foi reproduzido; o mod corrige apenas a recusa de copiar salas bloqueadas.

## Créditos
Requer o Polyphon (VizardAlpha).
