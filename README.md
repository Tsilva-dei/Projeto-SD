# 🔍 Googol - Motor de Pesquisa Web Distribuído

> Sistema de pesquisa web distribuído com backend RMI e interface Web moderna desenvolvida em Spring Boot


## Requisitos

- **Java JDK 17** ou superior
- **Maven 3.6+**
- **Conexão à Internet** (para APIs HackerNews e Gemini)

---

## Compilação e Build

O projeto utiliza **Maven** para gestão de dependências e build.

### Compilar e Gerar JARs

Na raiz do projeto, execute:

```bash
mvn clean package
```

**Isto irá:**
- Compilar todo o código
- Correr os testes
- Gerar o executável Web em `target/googol-web-1.0.0.jar`
- Compilar as classes RMI em `target/classes`

---

## Configuração

O ficheiro **`config.properties`** na raiz do projeto é **obrigatório**.

### Exemplo de configuração:

```properties
# RMI Configuration
rmi.host=localhost
rmi.port=1099

# Web Server Configuration
server.port=8080
server.host=localhost

# APIs Externas
gemini.api.key=A_TUA_CHAVE
```

> **Importante:** Substitua `A_TUA_CHAVE` pela sua chave API do Google Gemini

---

## Guia de Execução

O sistema é composto por um **Backend Distribuído (RMI)** e um **Frontend Web**.  
Devem ser iniciados pela ordem abaixo.

### Passo 1: Iniciar o Backend RMI

Abra **4 terminais separados** e execute os seguintes comandos:

#### Terminal 1 - URLQueue
```bash
java -cp "target/classes;lib/*" rmi.URLQueue
```

#### Terminal 2 - Storage Barrel
```bash
java -cp "target/classes;lib/*" rmi.StorageBarrel barrel1
```

#### Terminal 3 - Downloader
```bash
java -cp "target/classes;lib/*" rmi.Downloader d1
```

#### Terminal 4 - Gateway
```bash
java -cp "target/classes;lib/*" rmi.Gateway
```

---

### Passo 2: Iniciar o Servidor Web

Com o Backend a correr, inicie a aplicação Web num **5º terminal**:

#### Via Maven

```bash
mvn spring-boot:run
```

---

### Passo 3: Utilização

1. **Aceda ao browser:** [http://localhost:8080](http://localhost:8080)
2. **Indexar URLs:**
   - Insira URLs manualmente na caixa de indexação
   - Ou use o botão **"Indexar HackerNews"** para indexar automaticamente as top stories
3. **Pesquisar:** Faça pesquisas e veja os resultados paginados
4. **Estatísticas:** Acompanhe os gráficos em tempo real na página inicial (WebSockets)
5. **IA:** Nos resultados da pesquisa, clique em **"Gerar Resumo IA"** para ver a integração com o Gemini

---

## Execução Distribuída (2 Máquinas)

### Máquina 1 (Servidor RMI + Web)

1. Editar `config.properties`:
   ```properties
   rmi.host=IP_MAQUINA_1
   ```
2. Iniciar `URLQueue`, `Gateway` e a `WebApplication`

### Máquina 2 (Workers)

1. Editar `config.properties`:
   ```properties
   rmi.host=IP_MAQUINA_1
   ```
2. Iniciar `StorageBarrel` e `Downloader`

---

## Funcionalidades

### Backend (RMI)
- **Indexação Distribuída:** Manual e recursiva de URLs
- **Pesquisa Relevante:** Ordenação baseada em citações (incoming links)
- **Persistência:** Recuperação automática após falhas
- **Redundância:** Múltiplos Storage Barrels com dados replicados

### Frontend (Web)
- **Interface de Pesquisa:** Página web intuitiva
- **Paginação:** Resultados em grupos de 10
- **Incoming Links:** Visualização de páginas que referenciam um resultado
- **Dashboard em Tempo Real:** Estatísticas via WebSockets
- **Integração HackerNews:** Indexação automática das top stories
- **Resumo com IA:** Geração de resumos usando Google Gemini

---

## Resolução de Problemas

### Porta Ocupada
Se a porta 8080 estiver em uso, altere `server.port` no ficheiro `config.properties`.

```properties
server.port=8081
```

### API Key Inválida
Se o resumo da IA falhar, verifique se a chave no `config.properties` é válida.

### ClassNotFoundException
Certifique-se de que executou `mvn package` antes de tentar correr as classes RMI.

### Conexão RMI Falhada
Verifique se:
- O `rmi.host` no `config.properties` está correto
- A porta `1099` não está bloqueada por firewall
- Os componentes RMI foram iniciados pela ordem correta

---

## Autores

- **Diogo Saldanha**
- **Tiago Silva**

**Unidade Curricular:** Sistemas Distribuídos  
**Ano Letivo:** 2024/2025

---