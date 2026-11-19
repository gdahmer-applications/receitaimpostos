# Consulta fiscal de CNPJs e CT-e

Página estática para consultar dados públicos de CNPJ da Madesa e das transportadoras e, separadamente, os indicadores oficiais de uma classificação tributária de CT-e.

## Acesso

Após a publicação, o endereço esperado é <https://gdahmer-applications.github.io/receitaimpostos/>. No GitHub, em **Settings → Pages → Build and deployment → Source**, selecione **GitHub Actions** se ainda não estiver habilitado. A execução pode ser acompanhada em **Actions → Publicar consulta fiscal**; também pode ser iniciada manualmente.

## Consulta de CNPJ

Cole até 50 CNPJs, separados por linha, vírgula ou ponto e vírgula. A consulta é feita pelo navegador diretamente à [BrasilAPI](https://brasilapi.com.br/docs#tag/CNPJ), serviço de terceiros que usa a Minha Receita e dados públicos. Os CNPJs consultados não são enviados ao GitHub pelo site nem adicionados ao código ou aos resultados do Actions. A API externa recebe os CNPJs e pode manter seus próprios registros.

A página apresenta situação cadastral, localidade, opção pelo Simples quando informada e o último ano de histórico de IRPJ disponível. Essas informações podem estar desatualizadas: histórico de lucro real/presumido não confirma o regime atual ou futuro e a opção cadastral pelo Simples não informa, por si só, a opção por IBS/CBS dentro ou fora do DAS.

## Consulta da classificação do CT-e

Informe o código `cClassTrib` do documento e a data de referência disponível. A página apresenta os indicadores da Receita para crédito do adquirente de IBS/CBS e o vínculo com CT-e registrado na base; não identifica a classificação pelo CNPJ e não confirma automaticamente o crédito. As tabelas representam uma fotografia com data e versão fixas, devendo ser atualizadas e revisadas conforme a Receita publique mudanças.

## Código da Receita

O workflow baixa e copia os repositórios oficiais, incluindo seus arquivos de licença MIT, para `receita/`:

- Regime regular: `150cccbe414d4a5edea703e4f7a424af5aa222c7`.
- Simples Nacional: `c5b0f01c8351c3f0d0795cc617b29d233e9f98df`.

O workflow executa localmente a calculadora de regime regular e exporta as classificações de 01/01/2027 e 01/01/2028 para a página. Ele não envia os CNPJs à calculadora da Receita e não calcula ou confirma créditos individuais. A calculadora de Simples também é copiada para consulta e futura integração, mas não é executada pelo site atual.

## Limites

- Os cálculos de imposto e a apropriação de créditos dependem dos dados da operação, do CT-e, das regras aplicáveis e da apuração fiscal.
- O ICMS exige tratamento próprio; o código da calculadora da reforma não substitui as regras estaduais.
- Consulte o fiscal da empresa e os canais oficiais antes de usar o resultado para escrituração.
- Atualize as versões fixadas no workflow, no exportador e nesta documentação após validar uma nova versão oficial.

## Testes

Execute `npm test` com Node.js 20 ou superior. Os testes usam respostas simuladas e não enviam CNPJs reais.
