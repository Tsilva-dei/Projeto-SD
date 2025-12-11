# Googol: Motor de Pesquisa Web Distribuído

## 1. Objetivos do Projeto

O projeto **Googol** consiste num motor de pesquisa Web distribuído, desenvolvido em duas fases.
O sistema simula o funcionamento de motores de busca reais, integrando:
1.  **Backend Distribuído (Meta 1):** Indexação automática, armazenamento redundante e pesquisa via RMI.
2.  **Interface Web e Integração (Meta 2):** Portal Web acessível aos utilizadores, estatísticas em tempo real e enriquecimento de dados via APIs externas (HackerNews e IA Generativa).

O objetivo principal foi criar uma solução robusta, tolerante a falhas e escalável, aplicando conceitos como **RMI/RPC**, **Multicast Fiável**, **Arquitetura MVC**, **WebSockets** e **Integração REST**.

---

## 2. Funcionalidades Desenvolvidas

### 2.1 Backend (Core)
* **Indexação Distribuída:** Indexação manual e recursiva de URLs.
* **Pesquisa Relevante:** Pesquisa por termos com ordenação baseada em citações (número de *incoming links*).
* **Persistência:** Recuperação automática de estado após falhas (crashes).
* **Redundância:** Múltiplos *Storage Barrels* com dados replicados.

### 2.2 Frontend (Web)
* **Interface de Pesquisa:** Página web para consulta e submissão de URLs.
* **Paginação:** Resultados apresentados em grupos de 10.
* **Incoming Links:** Visualização das páginas que apontam para um resultado específico.
* **Dashboard em Tempo Real:** Atualização automática de estatísticas (Top pesquisas, Barrels ativos) via WebSockets.
* **Integração HackerNews:** Indexação das "Top Stories" do HackerNews via API REST.
* **Resumo com IA:** Geração de resumos contextualizados sobre a pesquisa usando a API Gemini (Google).

---

## 3. Arquitetura do Sistema

O sistema segue uma arquitetura multicamada, onde o servidor Web atua como cliente do sistema distribuído RMI.

### 3.1 Diagrama Geral

```
┌─────────────┐
│   Clients   │
└──────┬──────┘
       │ RMI
       ▼
┌─────────────┐
│   Gateway   │
└──────┬──────┘
       │ RMI
       ├──────────────┬──────────────┐
       ▼              ▼              ▼
┌──────────┐   ┌──────────┐   ┌──────────┐
│ Barrel 1 │   │ Barrel 2 │   │ Barrel N │
└──────────┘   └──────────┘   └──────────┘
       ▲              ▲              ▲
       │ Reliable     │ Multicast    │
       └──────┬───────┴───────┬─────┘
              │               │
       ┌──────▼──────┐ ┌─────▼─────┐
       │Downloader 1 │ │Downloader N│
       └──────┬──────┘ └─────┬─────┘
              │               │
              └──────┬────────┘
                     ▼
              ┌──────────┐
              │URL Queue │
              └──────────┘
```

### 3.2 Componentes Web (Meta 2)

A camada Web foi desenvolvida em **Java Spring Boot**, seguindo o padrão **MVC (Model-View-Controller)**:

1.  **WebApplication:** Ponto de entrada da aplicação Spring Boot. Carrega as configurações dinâmicas (IP, Porta) do ficheiro `config.properties`.
2.  **SearchController (Controller):** Gere os pedidos HTTP (`GET /search`, `POST /index`). Trata da lógica de apresentação e comunica com o serviço.
3.  **GoogolService (Service/Model):** Atua como a "ponte" entre o mundo Web e o RMI.
    * Mantém a referência para a interface remota `GatewayInterface`.
    * Gere a conexão e reconexão automática ao Gateway RMI.
    * Realiza chamadas REST às APIs externas (HackerNews e Gemini).
4.  **Thymeleaf (View):** Motor de templates usado para renderizar o HTML (`index.html`, `search.html`) no servidor.
5.  **StatsScheduler:** Componente agendado que consulta periodicamente o Gateway e envia estatísticas via WebSockets.

---

## 4. Integração e Protocolos

### 4.1 Spring Boot ↔ RMI
A integração foi isolada na classe `GoogolService`. Ao iniciar, o serviço localiza o RMI Registry (usando o IP/Porta definidos no `config.properties`) e obtém o stub do `Gateway`. Todas as exceções remotas (`RemoteException`) são capturadas e transformadas em mensagens amigáveis para o utilizador web.

### 4.2 WebSockets (Tempo Real)
Para as estatísticas, utilizou-se o protocolo **STOMP** sobre WebSockets.
* **Servidor:** O `StatsScheduler` publica mensagens no tópico `/topic/stats` a cada 3 segundos (configurável).
* **Cliente:** O `index.html` usa `sockjs` e `stomp.js` para subscrever o tópico e atualizar o DOM sem recarregar a página.

### 4.3 APIs REST Externas
A comunicação REST é feita através da classe `RestTemplate` do Spring:
* **HackerNews:** Ação assíncrona (thread separada) que obtém os IDs das *top stories* e, em seguida, os detalhes de cada história para indexar o URL.
* **Gemini AI:** Envia a *query* e excertos dos resultados para a API da Google, recebendo um resumo textual. A API Key é gerida de forma segura via configuração.

---

## 5. Configuração e Segurança

Para garantir flexibilidade e segurança, **todos** os parâmetros sensíveis foram movidos para o ficheiro `config.properties`, eliminando valores *hardcoded*:

* **RMI:** `rmi.host`, `rmi.port`
* **Web:** `server.port`, `server.host` (permite mudar a porta se a 8080 estiver ocupada)
* **API Keys:** `gemini.api.key` (proteção de credenciais)

A classe `Config` centraliza a leitura destas propriedades, permitindo alterar o comportamento do sistema sem recompilar o código.

---

## 6. Tratamento de Falhas e Robustez

Para além dos mecanismos da Meta 1 (multicast fiável, persistência), a Meta 2 introduziu:
1.  **Reconexão RMI na Web:** Se o Gateway falhar e reiniciar, o `GoogolService` deteta a perda de conexão e tenta restabelecer a ligação no próximo pedido.
2.  **Validação de Input:** O controlador valida URLs e termos de pesquisa vazios.
3.  **Gestão de Erros de APIs:** Falhas no Gemini ou HackerNews não derrubam a aplicação; são mostradas mensagens de erro elegantes na interface.

---

## 7. Guia de Instalação e Execução

### Pré-requisitos
* Java 17+
* Maven (opcional, wrapper incluído)
* `config.properties` configurado na raiz

### Passo 1: Iniciar Backend RMI (Meta 1)
Em terminais separados (ou máquinas diferentes):
1.  `java rmi.URLQueue`
2.  `java rmi.StorageBarrel barrel1`
3.  `java rmi.Downloader d1`
4.  `java rmi.Gateway`

### Passo 2: Iniciar Web Server (Meta 2)
```bash
# Compilar e criar o JAR
mvn clean package

# Executar
java -jar target/googol-web-1.0.0.jar
# Ou via Maven direto
mvn spring-boot:run
```


O servidor iniciará na porta definida em config.properties (default: 8080). Aceder via browser: http://localhost:8080 (ou IP da máquina).

---

## 8. Distribuição de Tarefas

O desenvolvimento foi dividido de forma a potenciar a especialização de cada elemento. Enquanto um membro focou na robustez e lógica distribuída do *backend* (o motor de pesquisa em si), o outro focou na acessibilidade e integração da camada Web.

| Elemento | Responsabilidades Principais |
|-----------|-----------------------------|
| **Tiago Silva** | Desenvolvimento dos componentes críticos RMI (`StorageBarrel`, `URLQueue`), implementação dos protocolos de tolerância a falhas e *reliable multicast*, gestão de concorrência e persistência de dados. Otimização do desempenho do *crawler* e da pesquisa. Implementação da camada de serviço (`GoogolService`) e controlador MVC com Spring Boot. Desenvolvimento do sistema de notificações em tempo real (WebSockets) e integração com APIs REST externas (HackerNews, Gemini). |
| **Diogo Saldanha** |  Desenvolvimento da interface de utilizador (Thymeleaf). Construção do relatório e documentação técnica do projeto |

**Tarefas partilhadas:** Definição dos contratos de interface RMI (`GatewayInterface`), testes de integração sistema-a-sistema e revisão final da entrega .
---

## 9. Testes Realizados

### 9.1 Testes Funcionais

| # | Teste | Descrição | Resultado |
|---|-------|-----------|-----------|
| 1 | Indexação Manual | Cliente indexa URL manualmente | ✅ PASS |
| 2 | Indexação Recursiva | Links são indexados automaticamente | ✅ PASS |
| 3 | Pesquisa Simples | Pesquisa por 1 palavra | ✅ PASS |
| 4 | Pesquisa Múltipla | Pesquisa por várias palavras | ✅ PASS |
| 5 | Ordenação | Ordenação por incoming links | ✅ PASS |
| 6 | Paginação | Resultados em grupos de 10 | ✅ PASS |
| 7 | Incoming Links | Consulta de ligações recebidas | ✅ PASS |
| 8 | Estatísticas | Top 10 pesquisas mais comuns | ✅ PASS |

### 9.2 Testes de Tolerância a Falhas

| # | Teste | Descrição | Resultado |
|---|-------|-----------|-----------|
| 11 | Barrel Crash | Barrel desligado durante indexação | ✅ Recuperação com retries |
| 12 | Barrel Recovery | Barrel reiniciado recupera estado | ✅ PASS |
| 13 | Gateway Failover | Pesquisa redirecionada a outro Barrel | ✅ PASS |
| 15 | Client Reconnect | Gateway reiniciada | ✅ PASS |

### 9.3 Testes de Performance

| # | Teste | Descrição | Resultado |
|---|-------|-----------|-----------|
| 19 | 1 Downloader | 100 URLs em ~300s | ✅ |
| 20 | 3 Downloaders | 100 URLs em ~120s | ✅ |
| 21 | Pesquisa Cache | Pesquisa repetida | ✅ 0.001s |

### 9.4 Testes Realizados (Meta 2)

| # | Teste | Descrição | Resultado |
|---|-------|-----------|-----------|
| W1 | Pesquisa Web | Pesquisar termos e verificar paginação e links | ✅ PASS |
| W2 | Incoming Links | Verificar listagem de links na interface Web | ✅ PASS |
| W3 | WebSockets | Abrir página inicial e indexar URL noutro terminal; verificar atualização automática das listas | ✅ PASS |
| W4 | HackerNews | Clicar "Indexar HackerNews" e verificar logs do Downloader a processar novos links | ✅ PASS |
| W5 | IA Summary | Gerar resumo numa pesquisa; verificar resposta contextualizada | ✅ PASS |
| W6 | Distribuição | Correr WebServer numa máquina e Backend noutra (editando `config.properties`) | ✅ PASS |
| W7 | Configuração | Alterar porta HTTP para 8081 e reiniciar; verificar acesso | ✅ PASS |

---

## 10. Conclusão

O projeto cumpre todos os requisitos propostos. A integração entre a arquitetura RMI legado e o moderno stack Web (Spring Boot) foi bem-sucedida, resultando num sistema distribuído funcional, interativo e extensível. A adição de WebSockets e IA valorizou significativamente a experiência de utilizador final.

---


**Autores:** Diogo Saldanha e Tiago Silva  
**Ano letivo:** 2025/26  
**Unidade Curricular:** Sistemas Distribuídos – Meta 2 (final)
