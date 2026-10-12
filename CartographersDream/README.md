# Cartographer's Dream

Mod de código para Songs of Syx V71 (0.71.44). Requer o Polyphon (VizardAlpha).

## O que faz
1. **Mapas de assentamento mais dramáticos.** Aumenta lagos, ilhas dentro de lagos, rios, cadeias de montanhas e a quantidade/tamanho/túneis das cavernas na geração do mapa da cidade (arquivo de dados `GenerationSettlement.txt`).
2. **Capital quase em qualquer lugar.** Na tela de fundar a capital, só é recusado o local em que os 9 tiles (3x3) são todos montanha. Montanha, lago, oceano, ilhota e rio largo passam.
3. **Capital cercada de água continua jogável.**
   - Se a capital começa (ou fica) sem estrada terrestre e o trono toca o mar aberto, imigrantes, caravanas, retorno de tropas, turistas e bandidos usam um **cais** (um tile de costa da ilha). Quem sai (emigrantes, caravanas, caçadores, emissários) anda até o cais e some dali.
   - No mapa-múndi, se o grafo de caminhos deixa a capital isolada, o mod liga a capital às 2 regiões mais próximas (comércio, exércitos, guarnições e bandidos funcionam). Só adiciona ligação quando detecta isolamento. Não regenera o mapa-múndi.
   - Capital em oceano ou montanha começa com uma ilha/platô irregular de uns 30 tiles de raio, limpo ao redor do ponto de chegada (senão não haveria onde pousar). Na capital no mar, a faixa de 6 tiles na borda do mapa vira água profunda (a água rasa a até 2 tiles de terra fica). Mapas de terra comum não são alterados.
   - O aviso "Capital Closed!" do jogo continua se você emparedar o próprio trono: o mod só ajuda quando a capital nasceu isolada pelo mar.
   - Tentativa de corrigir o problema conhecido "caçadores não trabalham em capital de oceano vazio": enquanto a capital é uma ilha, o jogo passa a achar um "caminho para fora" (o cais).
   - **Chegada de barco.** Na capital de ilha, quem chega vem no bote de pesca do jogo, em pé no barco com o visual da própria espécie: imigrantes, turistas e tropas que voltam saem da borda do mapa pelo mar e só aparecem em terra quando o barco encosta. Caravanas de mercadores vêm num barco com a mercadoria e seguem a pé depois de desembarcar. Invasores que surgem no mar, ou em terra do outro lado do mar, são levados de barco até o cais mais próximo e desembarcam ali. Vários barcos saindo do mesmo ponto partem em fila.

## Como usar
Nada a configurar. Ative o mod ao **começar uma campanha nova** e funde a capital onde quiser. Para ver a diferença nos mapas, gere um mundo novo.

## Aviso importante: só no início da campanha
Adicione o mod apenas ao iniciar uma campanha. As ligações do grafo do mundo ficam gravadas no save (o jogo sem o mod simplesmente as ignora), mas a capital de ilha passa a depender do mod para imigrar e comerciar. Remover no meio do jogo faz a capital de ilha voltar a "Capital Closed!". O mod grava só um número de versão e um indicador "capital é ilha"; carrega saves sem esses dados sem erro.

## Compatibilidade
- Usa mixins do Polyphon, sem substituir classes do jogo. Ponto por ponto: `WorldCentrePlacablity.terrain`, `world.map.pathing.Gen.generateAll`, `Initer.markArround`, `EntryPoints.update` e `randomReachable(int)`, `SFinderEntry.find/any/anyHas`, `ENTETIES.add`, `Humanoid.update`, `Caravan.init/update/render`, `HalfEnts.renderBelow`.
- O arquivo `assets/init/config/GenerationSettlement.txt` substitui o bloco `GENERATION` inteiro. Outro mod que também troque esse arquivo vence ou perde conforme a ordem de carga.
- **Heroes of Syx** (v2.14.2 ou mais novo): carregue este mod **depois** dele.
- Falhas dentro dos ganchos são capturadas e registradas no log uma vez; o jogo cai para o comportamento normal.

## Não implementado
- **Viagem de barco não é salva.** Se você salvar com barcos no meio do caminho, ao carregar o passageiro já está no ponto de chegada (ou, no caso de invasor, onde surgiu). A viagem começa no máximo 80 tiles antes da costa para ninguém esperar minutos num mapa grande de mar.
- **Formas novas (penínsulas, fiordes, cânions, vales).** O gerador de assentamento só expõe intensidades (tamanho de lago, largura de rio, etc.); o mod ajusta esses valores. Penínsulas e fiordes dependem do terreno do mapa-múndi, que o mod não regenera.
- **Correção para saves antigos.** O vínculo do mapa-múndi roda só na geração do mundo (campanha nova).
- Mapas maiores: fora do escopo.

## Limites conhecidos
- Tropas que voltam usam o mesmo caminho dos imigrantes (cais + barco), mas não foram testadas em jogo isoladamente.

## Créditos
Requer o Polyphon (VizardAlpha).
