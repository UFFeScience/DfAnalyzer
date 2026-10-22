#!/bin/sh
# Migrações para bancos já existentes; database-script.sql só roda num banco novo.
DB=dataflow_analyzer
PORT=50000

q() { mclient -p $PORT -d $DB -f csv -s "$1" 2>&1; }

has_column() {
  [ "$(q "SELECT COUNT(*) FROM sys.columns c JOIN sys.tables t ON t.id = c.table_id WHERE t.name = '$1' AND c.name = '$2';")" = "1" ]
}

add_column() {  # tabela coluna tipo
  if has_column "$1" "$2"; then
    echo "[migracao] $1.$2 ja existe"
    return 0
  fi
  q "ALTER TABLE $1 ADD COLUMN $2 $3;" >/dev/null
  if has_column "$1" "$2"; then
    echo "[migracao] $1.$2 criada"
  else
    echo "[migracao] ERRO: nao foi possivel criar $1.$2 - a origem do mapeamento nao sera gravada"
  fi
}

# espera o banco aceitar conexao (o `monetdb start` volta antes disso)
i=0
until [ "$(q 'SELECT 1;')" = "1" ] || [ $i -ge 30 ]; do i=$((i+1)); sleep 1; done

add_column dataflow_version origin "VARCHAR(10)"
add_column dataflow_version generator "VARCHAR(200)"
