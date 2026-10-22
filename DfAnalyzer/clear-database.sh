#!/bin/bash

DB_FARM=./data
TMPFILE=/tmp/delete_cmds.sql

echo "Starting database dataflow_analyzer..."
monetdbd start $DB_FARM 2>/dev/null
monetdb start dataflow_analyzer 2>/dev/null

echo "Generating DELETE commands..."
mclient -p 50000 -d dataflow_analyzer -f csv << 'EOF' > $TMPFILE.raw
SELECT 'DELETE FROM "' || s.name || '"."' || t.name || '";'
FROM sys.tables t
JOIN sys.schemas s ON t.schema_id = s.id
WHERE t.system = false
  AND t.type = 0;    -- 0 = table
EOF

cat $TMPFILE.raw \
    | sed 's/^"//; s/"$//' \
    | sed 's/""/"/g' \
    > $TMPFILE

rm $TMPFILE.raw

echo "Running DELETEs..."
mclient -p 50000 -d dataflow_analyzer < $TMPFILE

echo "Tabelas mantidas, views ignoradas, dados removidos com sucesso!"
echo "Comandos executados em: $TMPFILE"
