# Googol: Motor de Pesquisa Web Distribuído

## 1. Objetivos do Projeto

O projeto **Googol** tem como objetivo desenvolver um motor de pesquisa de páginas Web distribuído, com funcionalidades semelhantes a serviços como Google, Qwant ou Ecosia.  
O sistema realiza **indexação automática (Web crawler)** e **pesquisa distribuída (search engine)**, permitindo aos utilizadores:

- Indexar e pesquisar páginas Web;
- Usar **arquitetura cliente-servidor** com comunicação **RMI/RPC**;
- Garantir **disponibilidade e redundância** através de réplicas;
- Aplicar **reliable multicast** para consistência entre réplicas;
- Explorar **processamento paralelo** para aumentar desempenho.

---

## 2. Visão Geral

O Googol é uma aplicação distribuída composta por cinco componentes principais:

1. **Clients** – interface do utilizador;
2. **Gateway** – ponto de entrada e balanceador de carga;
3. **Storage Barrels** – servidores de armazenamento (réplicas);
4. **Downloaders** – módulos de download e análise de páginas;
5. **URL Queue** – gestor da fila de URLs a visitar.

Estes componentes comunicam entre si através de **RMI**. O sistema é capaz de indexar páginas Web de forma automática e recursiva, construindo um **índice invertido** que associa palavras às páginas onde aparecem.  
Durante as pesquisas, os resultados são ordenados por **relevância** (número de ligações recebidas de outras páginas).

---

## 3. Funcionalidades Desenvolvidas

1. **Indexar novo URL** – o utilizador pode introduzir manualmente um URL para indexação.  
2. **Indexação recursiva** – o sistema visita automaticamente todos os links encontrados nas páginas.  
3. **Pesquisa por termos** – permite buscar páginas que contenham todas as palavras indicadas.  
4. **Ordenação por importância** – resultados ordenados pelo número de ligações recebidas.  
5. **Consulta de ligações recebidas** – o utilizador pode ver que páginas apontam para um determinado URL.  
6. **Estatísticas em tempo real** – mostra as 10 pesquisas mais comuns, barrels ativos e tempos médios de resposta.  
7. **Stop Words** – não implementado nesta meta (funcionalidade opcional para grupos de 3).

---

## 4. Arquitetura do Sistema

### 4.1 Visão Geral

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

### 4.2 Componentes Principais

- **URLQueue** – gere a fila de URLs a visitar, evitando duplicados e garantindo persistência.  
- **Downloader** – faz download e análise das páginas com a biblioteca **jsoup**, extrai texto e links, e envia os dados indexados para todos os barrels.  
- **Storage Barrel** – armazena o índice invertido, mantém réplicas idênticas e garante persistência em disco.  
- **Gateway** – atua como ponto de entrada, distribuindo pedidos entre barrels e mantendo cache de resultados.  
- **Client** – interface de linha de comando, com menu interativo e estatísticas em tempo real.

---

## 5. Requisitos Não Funcionais

- **Reliable multicast** assegura que todos os barrels ativos recebem a mesma informação.  
- **Tolerância a falhas** implementada com retries, failover e recuperação automática.  
- **Persistência** de estado em ficheiro para todos os componentes.  
- **Balanceamento de carga** via round-robin na Gateway.  
- **Configuração centralizada** através de `config.properties`.  

---

## 6. Tratamento de Exceções e Failover

- Retries automáticos quando um Barrel falha temporariamente.  
- Failover automático da Gateway – se um Barrel não responder, tenta outro.  
- Downloaders continuam mesmo com falhas parciais.  
- Reconexão automática de clientes se a Gateway reiniciar.  
- Health monitoring a cada 10 segundos com `ping()` remoto.  

---

## 7. Decisões Técnicas

- **ConcurrentHashMap** usado por ser thread-safe e eficiente.  
- **Atomic write** com ficheiro temporário evita corrupção de dados.  
- **Round-Robin** escolhido por simplicidade e previsibilidade.  
- **Cache** na Gateway para acelerar pesquisas repetidas.  
- **ACK/NACK booleano** em `indexPage()` para multicast fiável.

---

## 8. Distribuição de Tarefas

| Elemento | Número | Responsabilidades Principais |
|-----------|---------|-----------------------------|
| **Diogo Saldanha** | 1 | Downloader completo, multicast fiável, comunicação com barrels, testes de indexação. |
| **Tiago Silva** | 2 | Gateway e URLQueue, StorageBarrel, client e testes de pesquisa e failover. |

**Tarefas partilhadas:** arquitetura, interfaces RMI, configuração, persistência e relatório.

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

---

## 10. Limitações e Trabalho Futuro

- Falta de “stop words” e “Bloom filter”;  
- PageRank simples (baseado apenas em incoming links);  
- Sincronização automática de novos Barrels ainda não implementada.

**Meta 2** incluirá:  
- Interface Web (HTML/Spring);  
- Integração com API REST;  
- Autenticação;  
- Pesquisas avançadas e PageRank completo.

---

## 11. Como Executar

### Pré-requisitos

```bash
Java 11+
jsoup-1.17.2.jar em /lib/
config.properties na raiz
```

### Exemplo de Execução

```bash
# Máquina 1
java URLQueue
java StorageBarrel barrel1
java Downloader d1
java Gateway

# Máquina 2
java StorageBarrel barrel2
java Downloader d2
java Client
```

Adicionar URLs iniciais no cliente:  
```
http://www.uc.pt
http://www.dei.uc.pt
```

---

## 12. Conclusão

O Googol cumpre integralmente os objetivos da Meta 1:  
- Indexação automática e recursiva;  
- Pesquisa distribuída com redundância;  
- Reliable multicast e failover;  
- Processamento paralelo e persistência;  
- Estatísticas em tempo real e interface funcional.  

**Autores:** Diogo Saldanha e Tiago Silva  
**Ano letivo:** 2025/26  
**Unidade Curricular:** Sistemas Distribuídos – Meta 1
