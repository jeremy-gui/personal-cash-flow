export interface ImportSummary {
  id: number;
  accountId: number;
  filename: string;
  importedAt: string;
  rowCount: number;
  createdCount: number;
  skippedCount: number;
}
