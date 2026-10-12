# Fixing the Syx

Pacote de ajustes de qualidade de vida para Songs of Syx (V71, 0.71.44). Cinco funções independentes, sem dados no save: é seguro adicionar ou remover no meio de uma campanha. Usa mixins do Polyphon, sem substituir classes do jogo.

Requer o Polyphon (VizardAlpha).

## Instalação
Veja a instalação no README da raiz do repositório.

## Como usar

1. **Casas sobre rocha de montanha.** Planta interna (casas etc.) com a porta virada para rocha deixa de ser rejeitada como "blocked". A obra escava a entrada sozinha. Só vale quando a rocha na frente da porta tem um tile de espessura (do outro lado há chão livre); porta virada para rocha mais funda continua bloqueada, para os construtores não ficarem presos lá dentro.
2. **Fileira de decorações** (só categoria Decorações, ferramenta de item único):
   - Ctrl+Alt+roda: quantidade (1 a 64).
   - Ctrl+Shift+roda: espaçamento em tiles, centro a centro.
   - R: gira a direção (leste, sul, oeste, norte). Com o mouse sobre o mapa.
   - Clique: coloca a fileira. Fantasmas translúcidos mostram as cópias; o texto "Row xN, step S" aparece na caixa de informação.
   - Espaçamento padrão: família monumento (tochas etc.) = 2 x raio - 1; demais decorações = largura do item.
3. **Pescaria no rio.** Com a ferramenta de pescaria, Ctrl+clique num tile de água rasa preenche a água conectada, do mais próximo para o mais longe, respeitando tamanho e dimensão máximos da sala (contando o que já foi pintado) e pulando tiles de outras salas. Você ainda confirma a sala e põe os itens.
4. **Estradas na montanha.** Arraste ou clique com a ferramenta de estrada (qualquer tipo) sobre rocha: ao soltar, cada tile de montanha recebe "escavar, depois estrada". Quando o tile é escavado, a estrada entra na fila. Apagar o túnel enfileirado libera o tile. Tecla T com a ferramenta de estrada ativa liga/desliga (padrão ligado a cada abertura do jogo). Ligado: tiles de montanha aparecem como válidos (verde); desligado: vermelhos como no jogo base.
5. **Pilares de sustentação.** Construindo uma sala interna por área, Ctrl+clique num tile amarelo: o mod planeja uma grade simétrica centrada na sala e remove um aglomerado por vez (cerca de 0,5 s cada), só onde ainda há amarelo por perto e sem desconectar a sala. Pasto dentro de montanha: pilares 1x1 numa grade de 8 tiles. Demais casos: aglomerados em "+" numa grade de 8 tiles. Simetria acima de completude: alguns amarelos podem sobrar.

## Compatibilidade
- Sem dados no save. Convive com outros mods; usa só ganchos Polyphon (ModifyReturn e Inject).
- Se um gancho falhar, o erro é registrado uma vez no log do jogo (`[FixingTheSyx]`) e o comportamento vanilla continua.

## Limites conhecidos
- Estradas: a lista "escavar, depois estrada" fica só na memória. Se você recarregar o save antes da escavação terminar, o túnel continua mas a estrada precisa ser colocada de novo.
- Estradas: com T ligado todos os tiles de rocha da seleção ficam verdes, não apenas o tile sob o cursor; não há o ícone "anti".
- Fileira de decorações: o raio usado é o raio base do monumento pelo tamanho do item (o código do jogo não varia o raio por nível de melhoria). Decorações que não são monumento usam a largura do item.
- Fileira: R gira a fileira e não o item. Cópias que não cabem são ignoradas, as demais são colocadas.
- Pilares: o centro da grade usa o meio da caixa envolvente; em salas de largura par a simetria é de meio tile. Clusters que tocam itens já colocados são ignorados.
- Pescaria: em rios muito longos, só o trecho mais próximo do clique (até o limite da sala) é preenchido.

## Não implementado
Nada da lista original foi cortado, mas as duas diferenças acima (tile verde só sob o cursor com ícone "anti", e raio por nível de melhoria) são aproximações.
