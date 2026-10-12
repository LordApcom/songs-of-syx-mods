# Songs of Syx mods

Cinco mods de código para **Songs of Syx V71 (0.71.44)**, cada um na sua pasta. Todos usam o **Polyphon** (de VizardAlpha) para se encaixar no jogo sem substituir classes, então convivem entre si e com outros mods feitos no Polyphon.

*English: five code mods for Songs of Syx V71 (0.71.44), one per folder. All of them need the Polyphon mod. Each folder has its own README (in Portuguese) with what the mod does, how to use it and its known limits.*

| Mod | O que faz |
| --- | --- |
| [Workplace Species Filter](WorkplaceSpeciesFilter) | Escolha, prédio por prédio, quais espécies e classes (cidadão / escravo) podem trabalhar nele e quais podem usar o serviço dele. |
| [Plan Ahead](PlanAhead) | Planeje qualquer prédio antes de pesquisar a tecnologia. Ele fica como plano parado e só pode ser ativado depois da pesquisa. |
| [Fixing the Syx](FixingTheSyx) | Pacote de qualidade de vida: casas encostadas na rocha, fileira de decorações, pescaria que preenche o rio, estradas através da montanha e pilares de sustentação automáticos. |
| [Cartographer's Dream](CartographersDream) | Mapas de cidade mais dramáticos e capital em quase qualquer lugar (montanha, lago, oceano, ilhota). Uma capital cercada de água continua jogável: imigrantes, comércio e invasores chegam de barco. |
| [Species Customs Gate](SpeciesCustomsGate) | Cada portão ganha um filtro de espécie e classe e uma seta de mão única. Inimigos e turistas sempre passam. |

## Requisitos
- Songs of Syx **V71 (0.71.44)**.
- O mod **Polyphon** instalado e ativo no launcher, junto com os mods daqui.
- Para compilar: **JDK 21** (`javac` e `jar` no PATH) e PowerShell.

## Como compilar e instalar
Cada pasta tem um `build.ps1`. Ele precisa saber onde estão o jogo e o `Polyphon.jar` (dentro da pasta `V71\script` do mod Polyphon instalado):

```powershell
cd WorkplaceSpeciesFilter
.\build.ps1 -Polyphon "<caminho>\Polyphon.jar"           # só compila, saída em dist\
.\build.ps1 -Polyphon "<caminho>\Polyphon.jar" -Deploy   # compila e copia para %APPDATA%\songsofsyx\mods
```

- Se o jogo não estiver na pasta padrão da Steam, passe `-Game "<pasta do jogo>"`.
- Também dá para usar as variáveis de ambiente `SOS_GAME` e `SOS_POLYPHON` em vez dos parâmetros.
- Depois, ative o Polyphon e os mods no launcher do jogo. Na primeira abertura o Polyphon pode pedir para reiniciar o jogo.

## Estrutura de cada mod
```
<Mod>/
  _Info.txt          nome, versão e descrição mostrados no launcher
  build.ps1          compila e (com -Deploy) instala
  src/               código Java
  resources/         META-INF/polyphon.json (lista de ganchos do Polyphon)
  assets/            dados do jogo que o mod troca (só no Cartographer's Dream)
  README.md          o que faz, como usar, compatibilidade e limites
```

## Contribuir
O repositório é público: qualquer pessoa pode baixar, compilar, fazer um fork e abrir uma issue. Só o dono do repositório envia mudanças e aceita pull requests.

## Licença
[MIT](LICENSE).
