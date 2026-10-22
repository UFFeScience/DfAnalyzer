# =============================================================================
# Estágio 1 — build do jar a partir do fonte.
# =============================================================================
FROM --platform=linux/amd64 maven:3.8-eclipse-temurin-8 AS builder

WORKDIR /build

# Pom primeiro, para as dependências ficarem em cache.
COPY DfAnalyzer/pom.xml .
RUN mvn -B -q dependency:go-offline || true

# O mvn package compila o Studio para dentro do jar.
COPY DfAnalyzer/studio ./studio

COPY DfAnalyzer/src ./src

# Sem -DskipTests: se os testes falharem, a imagem não é gerada.
RUN mvn -B package

# =============================================================================
# Estágio 2 — imagem de execução.
# =============================================================================
FROM --platform=linux/amd64 ubuntu:22.04

# Sem isto a instalação do tzdata pede o fuso e o build trava.
ENV DEBIAN_FRONTEND=noninteractive

# Locale UTF-8.
RUN apt-get update && apt-get install -y \
    curl \
    build-essential \
    git \
    libcurl4-openssl-dev \
    psmisc \
    python3-pip \
    unzip \
    vim \
    wget \
    zip \
    openjdk-11-jdk \
    tzdata \
    && apt-get clean

# Set environment variables for Java
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

ENV JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
ENV PATH=$JAVA_HOME/bin:$PATH

# Set the MonetDB version to be installed
ENV MONETDB_VERSION=11.49.11

# Download MonetDB packages
RUN wget -q https://www.monetdb.org/downloads/deb/archive/jammy/monetdb5-sql_${MONETDB_VERSION}_amd64.deb \
    && wget -q https://www.monetdb.org/downloads/deb/archive/jammy/monetdb-client_${MONETDB_VERSION}_amd64.deb \
    && wget -q https://www.monetdb.org/downloads/deb/archive/jammy/monetdb5-server_${MONETDB_VERSION}_amd64.deb \
    && wget -q https://www.monetdb.org/downloads/deb/archive/jammy/libmonetdb-client26_${MONETDB_VERSION}_amd64.deb \
    && wget -q https://www.monetdb.org/downloads/deb/archive/jammy/libmonetdb-stream26_${MONETDB_VERSION}_amd64.deb \
    && wget -q https://www.monetdb.org/downloads/deb/archive/jammy/libmonetdb28_${MONETDB_VERSION}_amd64.deb

# Install MonetDB packages and clean up
RUN dpkg -i libmonetdb-stream26_${MONETDB_VERSION}_amd64.deb \
    && dpkg -i libmonetdb-client26_${MONETDB_VERSION}_amd64.deb \
    && dpkg -i libmonetdb28_${MONETDB_VERSION}_amd64.deb \
    && dpkg -i monetdb5-server_${MONETDB_VERSION}_amd64.deb \
    && dpkg -i monetdb5-sql_${MONETDB_VERSION}_amd64.deb \
    && dpkg -i monetdb-client_${MONETDB_VERSION}_amd64.deb \
    && rm -f monetdb5-sql_${MONETDB_VERSION}_amd64.deb \
    && rm -f monetdb-client_${MONETDB_VERSION}_amd64.deb \
    && rm -f monetdb5-server_${MONETDB_VERSION}_amd64.deb \
    && rm -f libmonetdb-client26_${MONETDB_VERSION}_amd64.deb \
    && rm -f libmonetdb-stream26_${MONETDB_VERSION}_amd64.deb \
    && rm -f libmonetdb28_${MONETDB_VERSION}_amd64.deb

# Copy the content of the DfAnalyzer directory to the Docker image
COPY DfAnalyzer/ /opt/DfAnalyzer/

# O jar compilado no primeiro estágio é o único que entra na imagem.
COPY --from=builder /build/target/DfAnalyzer-1.0.jar /opt/DfAnalyzer/target/DfAnalyzer-1.0.jar

# Set the working directory and ensure script permissions
WORKDIR /opt/DfAnalyzer

RUN chmod +x start-dfanalyzer.sh

RUN chmod +x restore-database.sh

# Run the restore-database.sh script
RUN ./restore-database.sh

COPY example /opt/example/

# Downloads do PyPI resistentes a rede instável.
ENV PIP_DEFAULT_TIMEOUT=100
ENV PIP_RETRIES=10
RUN pip3 install --no-cache-dir -U pip
ENV PIP_RESUME_RETRIES=50

# Install pytest
RUN pip3 install pytest==7.3.1

# Copy the 'dfa-lib-python' directory to the root of the Docker image.
COPY library/dfa-lib-python /dfa-lib-python/

# Install the Python package.
WORKDIR /dfa-lib-python
# Repete a instalação em caso de queda de conexão.
RUN for i in 1 2 3; do \
        pip3 install --no-cache-dir . && break; \
        echo "pip install falhou (tentativa $i/3) - tentando de novo"; \
        [ "$i" = 3 ] && exit 1; \
    done

# Expose necessary ports.
EXPOSE 22000 50000

WORKDIR /opt/DfAnalyzer

# Set the default command to start the database and shell
CMD ["/bin/bash"]
