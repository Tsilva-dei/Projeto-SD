# Googol - Motor de Pesquisa Web Distribuído

## Requisitos

- Java JDK 11 ou superior
- Biblioteca jsoup (incluída em `lib/jsoup-1.17.2.jar`)
- Sistema Operativo: Windows, Linux ou macOS

Verificar instalação Java:
```bash
java -version
javac -version
```

---

## Compilação

### Linux/Mac
```bash
mkdir -p bin
javac -d bin -cp ".:lib/*" src/*.java
```

### Windows PowerShell
```powershell
New-Item -ItemType Directory -Force -Path bin
javac -d bin -cp ".;lib/*" src/*.java
```

---

## Configuração

O ficheiro `config.properties` contém todas as configurações. Não requer recompilação após alterações.

### Execução em 1 Máquina (Teste Local)
Deixar configuração default:
```properties
rmi.host=localhost
rmi.port=1099
```

### Execução em 2 Máquinas (Distribuído)

**Máquina 1:**
```properties
rmi.host=localhost
rmi.port=1099
```

**Máquina 2:**
```properties
rmi.host=192.168.X.X    (IP da Máquina 1)
rmi.port=1099
```

Descobrir IP da Máquina 1:
```bash
# Linux/Mac
ifconfig

# Windows
ipconfig
```

---

## Execução em 1 Máquina

Abrir 5 terminais separados na pasta do projeto e executar pela ordem:

### Terminal 1: URLQueue
```bash
# Linux/Mac
java -cp ".:lib/*:bin" URLQueue

# Windows
java -cp ".;lib/*;bin" URLQueue
```

### Terminal 2: Storage Barrel
```bash
# Linux/Mac
java -cp ".:lib/*:bin" StorageBarrel barrel1

# Windows
java -cp ".;lib/*;bin" StorageBarrel barrel1
```

### Terminal 3: Downloader
```bash
# Linux/Mac
java -cp ".:lib/*:bin" Downloader d1

# Windows
java -cp ".;lib/*;bin" Downloader d1
```

### Terminal 4: Gateway
```bash
# Linux/Mac
java -cp ".:lib/*:bin" Gateway

# Windows
java -cp ".;lib/*;bin" Gateway
```

### Terminal 5: Client
```bash
# Linux/Mac
java -cp ".:lib/*:bin" Client

# Windows
java -cp ".;lib/*;bin" Client
```

---

## Execução em 2 Máquinas

### Máquina 1 (iniciar primeiro)

Terminal 1:
```bash
java -cp ".;lib/*;bin" URLQueue
```

Terminal 2:
```bash
java -cp ".;lib/*;bin" StorageBarrel barrel1
```

Terminal 3:
```bash
java -cp ".;lib/*;bin" Downloader d1
```

Terminal 4:
```bash
java -cp ".;lib/*;bin" Gateway
```

### Máquina 2 (iniciar após Máquina 1)

Terminal 1:
```bash
java -cp ".;lib/*;bin" StorageBarrel barrel2
```

Terminal 2:
```bash
java -cp ".;lib/*;bin" Downloader d2
```

Terminal 3:
```bash
java -cp ".;lib/*;bin" Client
```

---

## Utilização do Cliente

O Cliente apresenta um menu com as seguintes opções:

```
1. Index a URL          - Adicionar URL para indexação
2. Search               - Pesquisar por palavras
3. Get incoming links   - Ver páginas que apontam para um URL
4. View statistics      - Ver estatísticas do sistema
5. View statistics (RT) - Estatísticas em tempo real
6. Exit                 - Sair
```

### Exemplo de Utilização

1. Indexar URL:
```
Choose: 1
Enter URL:  http://www.uc.pt
            http://www.dei.uc.pt
            http://www.fctuc.pt
            http://eden.dei.uc.pt
            https://en.wikipedia.org/wiki/University_of_Coimbra
```

2. Aguardar 10-15 segundos (processamento)

3. Pesquisar:
```
Choose: 2
Enter search terms: universidade
```

4. Navegar resultados: `n` (next), `p` (previous), `b` (back)

---

## URLs para Teste

```
http://www.uc.pt
http://www.dei.uc.pt
http://www.fctuc.pt
https://en.wikipedia.org/wiki/University_of_Coimbra
```

---

## Resolução de Problemas

### "Connection refused"
- Verificar se URLQueue está a correr
- Verificar config.properties (rmi.host correto)
- Em 2 máquinas: verificar firewall e conectividade de rede

### "ClassNotFoundException"
- Verificar CLASSPATH correto
- Linux/Mac: usar `:` (dois pontos)
- Windows: usar `;` (ponto-e-vírgula)

### "No barrels available"
- Verificar se pelo menos 1 StorageBarrel está a correr
- Verificar logs do Gateway

### "Address already in use"
- Matar processos Java anteriores
- Ou alterar `rmi.port` em config.properties

### Firewall (Windows - Máquina 1)
```powershell
# PowerShell como Administrador
New-NetFirewallRule -DisplayName "RMI Googol" -Direction Inbound -Protocol TCP -LocalPort 1099 -Action Allow
```

---

## Notas Importantes

- Aguardar cada componente inicializar completamente antes de iniciar o próximo
- URLQueue deve ser iniciado primeiro (cria RMI registry)
- Em execução distribuída, Máquina 1 deve estar completamente iniciada antes de iniciar Máquina 2
- Resultados de pesquisa são ordenados por número de incoming links
- Sistema guarda estado automaticamente em `data/` (recuperação após crash)
- Logs de execução podem ser consultados nos terminais