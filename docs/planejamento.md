# Planejamento e estrutura

## Resumo dos requisitos
| Área | O que o sistema precisa fazer |
|---|---|
| Restrições | Java Core puro, versão LTS, console, sem bibliotecas, frameworks, banco de dados ou web |
| Clientes | Cadastrar, consultar, associar contas, validar dados, evitar identificadores duplicados |
| Contas | `ContaBancaria` abstrata; corrente e poupança com regras próprias; número, id, titular, saldo, abertura, status, histórico; encapsulamento rigoroso do saldo |
| Operações | Depósito, saque, transferência, saldo, extrato, bloqueio/desbloqueio por usuário autorizado |
| Dinheiro | `BigDecimal` com regras explícitas de escala, arredondamento, nulos, sinais, comparação, juros |
| Transferência | Operação única; validar antes de alterar; id comum; nunca deixar só uma conta alterada |
| Auditoria | Registro completo de cada movimentação; histórico sem edição; correções por lançamento compensatório |
| Exceções | Seis exceções nomeadas, critério checked × unchecked, mensagens amigáveis |
| Modo Investigativo | Oito cenários fictícios, isolados das contas, com relatório padronizado e separação entre fato, alegação e ficção |
| Qualidade | Compila sem dependências, não cai com entrada inválida, testes sem framework, exemplos reproduzíveis |

## Ambiguidades identificadas e decisões que foram tomadas

| # | Ambiguidade | Decisão                                                                                                                                                                              |
|---|---|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| P1 | Qual versão LTS? | **Java 25**: traz records, `sealed`, *pattern matching* em `switch` e `instanceof`, text blocks.                                                                                     |
| P2 | "Usuário autorizado" para bloqueio não foi definido | Três perfis: **Caixa**, **Gerente** e **Auditoria**. O operador é escolhido no início da sessão. Não há senha: autenticação está fora do escopo (o foco é autorização e segregação). |
| P3 | Regras específicas de corrente e poupança | Corrente: cheque especial (0 a R$ 10.000, concedido só por gerente). Poupança: sem saldo negativo, até 3 débitos/mês, rendimento mensal fixo de 0,5%. Simplificação didática declarada. |
| P4 | Taxas e tarifas | Há juros (rendimento da poupança). Não há tarifas, por decisão: tarifa seria mais um lançamento com a mesma mecânica, sem ensinar nada novo.                                         |
| P5 | O "resultado" de cada movimentação | Lançamentos do razão só existem se efetivados. O **resultado** (SUCESSO, REJEITADA, FALHA_REVERTIDA) fica na trilha de auditoria, que registra também as tentativas recusadas.       |
| P6 | Atomicidade em memória | Trava única + validação completa antes de alterar + compensação automática em falha. Detalhes em [06-ANALISE-TECNICA.md](06-ANALISE-TECNICA.md).                                     |
| P7 | Entrada com mais de 2 casas decimais | **Rejeitada**, não arredondada. Só valores *calculados* são arredondados (HALF_EVEN).                                                                                                |
| P8 | Encerramento de conta | Só com saldo zero; estado final.                                                                                                                                                     |
| P9 | Estorno | Só gerente, com motivo, e nunca pelo mesmo operador que executou a transação original.                                                                                               |
| P10 | Fatos sobre o caso real | Pesquisados em fontes jornalísticas e citados com link, data e tipo (fato, declaração oficial, alegação, opinião). Sem nomes de pessoas.                                             |
| P11 | Identificadores | Sequenciais e determinísticos (CLI-0001, 10001-3, TX-000001) para que exemplos e testes sejam reproduzíveis.                                                                         |

**Viabilidade só com Java Core**: tudo é coberto pelo JDK — `BigDecimal`, `java.time`, `Collections`, `MessageDigest` (SHA-256), `HexFormat`, `BufferedReader`. Nenhum requisito exigiu biblioteca.

## Arquitetura proposta 

Camadas com dependências em um só sentido:

```
ui ──► service ──► model
         │  ├────► repository (interfaces)
         │  └────► audit
investigativo ──► service (somente em banco sandbox próprio)
app ──► tudo (composição)
```

**Por que esta arquitetura**

- **Regras longe da interface**: menus só coletam dados e mostram resultados. Trocar o console por uma API REST exigiria uma nova camada `ui`, sem tocar em `service` ou `model`.
- **Repositórios por interface**: os serviços dependem de `ContaRepository`, não de `HashMap`. Persistência real seria uma nova implementação.
- **Composição manual** (`NucleoBancario.criar`): injeção de dependências sem framework, explícita e testável. O mesmo método cria o banco principal, o banco de cada teste e o *sandbox* do cenário C8.
- **Auditoria como pacote próprio**: a trilha e o conciliador não dependem dos serviços; são controles independentes, como deveriam ser.
- **Sem abstrações desnecessárias**: não há `ServiceFactory`, `AbstractRepository` genérico ou camadas DTO duplicadas; `ResumoConta` existe porque protege o encapsulamento.

## Principais classes e responsabilidades

| Classe | Responsabilidade |
|---|---|
| `Dinheiro` | *Value object* monetário: escala 2, HALF_EVEN, parse pt-BR, comparação, formatação |
| `Cpf` | Validação por dígitos verificadores; `toString` mascarado |
| `Cliente` | Titular imutável, nome normalizado |
| `ContaBancaria` (`sealed`) | Estado e invariantes: saldo só muda por lançamento; validação antes da mutação; ciclo de vida |
| `ContaCorrente` / `ContaPoupanca` | Polimorfismo: limite de crédito, regras de débito, rendimento |
| `Lancamento` / `DadosLancamento` | Registro imutável do razão / pedido de lançamento |
| `Operador`, `Perfil`, `Permissao` | Autorização e segregação de funções |
| `ClienteService` | Cadastro e consulta com validações |
| `ContaService` | Abertura, bloqueio, desbloqueio, encerramento, consulta, extrato |
| `OperacaoService` | Depósito, saque, transferência (fases + compensação), estorno, rendimento |
| `AuditoriaService` | Acesso controlado à trilha e aos controles detectivos |
| `TrilhaAuditoria` / `RegistroAuditoria` | Log *append-only* com hash SHA-256 encadeado |
| `ConciliadorContas` | Saldo × histórico, saldo resultante, partidas dobradas, estornos |
| `PontoDeFalha` / `InjetorDeFalhas` | Injeção de falha para provar a estratégia de consistência |
| `CenarioInvestigativo` + 8 implementações | `calcular()` tipado e testável; `executar()` gera relatório |
| `ReferenciasPublicas` | Catálogo classificado de fatos, declarações, alegações e opiniões |
| `MenuBase` e menus | Laço de menu com tratamento centralizado de erros |


## Regras de negócio

**Clientes**: nome de 3 a 100 caracteres (letras, espaço, apóstrofo, hífen); CPF válido e único; idade entre 18 e 130 anos; nascimento não pode estar no futuro.

**Contas**
- Corrente: saldo disponível = saldo + cheque especial. Limite entre R$ 0 e R$ 10.000; acima de zero só com perfil Gerente.
- Poupança: saldo disponível = saldo; até 3 débitos (saques ou transferências enviadas) por mês-calendário, sem contar transferências que falharam e foram compensadas; rendimento de 0,5% a.m. sobre o saldo, uma vez por mês, arredondado com HALF_EVEN.
- Status: ATIVA ⇄ BLOQUEADA; ATIVA → ENCERRADA (saldo zero, definitivo). Mudanças exigem motivo (≥ 10 caracteres) e perfil autorizado.
- Só conta ATIVA recebe ou envia valores.

**Operações**
- Valor sempre > 0, com no máximo 2 casas decimais.
- Débito só se `valor ≤ saldo disponível` e se as regras do produto permitirem.
- Transferência: origem ≠ destino; ambas existem e estão ativas; mesmo `idTransacao` no débito e no crédito.
- Estorno: perfil Gerente; motivo; operador diferente de quem executou; transação existe, não foi estornada, não é estorno nem transferência já revertida; quem devolve o valor precisa de saldo disponível.

**Auditoria**: toda tentativa gera registro com protocolo, data/hora, tipo, operador, contas, valor, resultado e descrição. Nenhum registro é editado ou removido.

## Riscos de inconsistência e decisões
| Risco | Mitigação adotada |
|---|---|
| Alterar saldo sem rastro | Sem *setter*; saldo só muda em `aplicar(lancamento)`, que registra o lançamento no histórico no mesmo passo |
| Transferência pela metade | Fase 1 valida tudo sem efeitos; fase 2 debita, credita e, se algo falhar depois do débito, compensa com o mesmo protocolo |
| Duas operações intercaladas | `synchronized` em uma trava única no `OperacaoService` |
| Arredondamento silencioso | Entrada com mais de 2 casas é rejeitada; só cálculos são arredondados (HALF_EVEN) |
| Comparação errada de `BigDecimal` | `equals` de `Dinheiro` usa `compareTo`; escala sempre 2 |
| Correção por edição | Histórico imutável; estorno = novos lançamentos referenciando o original |
| Adulteração de registros | Hash SHA-256 encadeado; verificação detecta alteração e remoção |
| UI movimentando conta por fora | Serviços devolvem `ResumoConta`/`Extrato`, nunca a entidade |
| Simulação contaminar o banco | Cenários usam dados próprios; C8 cria um `NucleoBancario` sandbox |
| Perda de dados ao encerrar | Limitação aceita e documentada (persistência em memória) |   

