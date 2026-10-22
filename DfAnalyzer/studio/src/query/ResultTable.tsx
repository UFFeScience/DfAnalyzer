import { Box, useTheme } from '@mui/material';
import { DataGrid, type GridColDef } from '@mui/x-data-grid';
import { useMemo } from 'react';
import type { QueryResult } from '../api/catalog';
import { MONO } from '../theme/tokens';

export function ResultTable({ result }: { result: QueryResult }) {
  const { studio } = useTheme();

  const columns: GridColDef[] = useMemo(
    () =>
      result.columns.map((column, index) => ({
        field: `c${index}`,
        headerName: column.name,
        description: column.type ? `${column.name} · ${column.type}` : column.name,
        flex: 1,
        minWidth: 150,
        type: column.type === 'NUMERIC' ? ('number' as const) : ('string' as const),
        renderCell: (params) =>
          params.value === null || params.value === undefined ? (
            <Box component="span" sx={{ color: studio.muted }}>—</Box>
          ) : (
            String(params.value)
          ),
      })),
    [result.columns, studio.muted],
  );

  const rows = useMemo(
    () =>
      result.rows.map((row, index) => {
        const record: Record<string, unknown> = { id: index };
        row.forEach((cell, column) => {
          const type = result.columns[column]?.type;
          record[`c${column}`] =
            cell !== null && type === 'NUMERIC' ? Number(cell) : cell;
        });
        return record;
      }),
    [result.rows, result.columns],
  );

  return (
    <Box sx={{ flexGrow: 1, minHeight: 240, px: 2, pb: 2, pt: 1.5 }}>
      <DataGrid
        rows={rows}
        columns={columns}
        density="compact"
        disableRowSelectionOnClick
        hideFooterSelectedRowCount
        sx={{
          height: '100%',
          border: `1px solid ${studio.border}`,
          borderRadius: '10px',
          color: studio.ink,
          fontFamily: MONO,
          fontSize: 12,
          '& .MuiDataGrid-columnHeaders': {
            bgcolor: studio.surface,
            borderBottom: `1px solid ${studio.border}`,
          },
          '& .MuiDataGrid-columnHeaderTitle': {
            fontFamily: 'inherit',
            fontSize: 11,
            fontWeight: 600,
            color: studio.muted,
            letterSpacing: '.02em',
          },
          '& .MuiDataGrid-cell': { borderColor: studio.border },
          '& .MuiDataGrid-footerContainer': {
            borderTop: `1px solid ${studio.border}`,
            minHeight: 40,
          },
          '& .MuiTablePagination-root': { color: studio.muted, fontSize: 12 },
        }}
      />
    </Box>
  );
}
