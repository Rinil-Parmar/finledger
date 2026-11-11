export interface Account {
  externalId: string;
  ownerName: string;
  currency: string;
  status: string;
  balance: number;
}

export interface Totals {
  cashPool: number;
  totalLiabilities: number;
  balanced: boolean;
}

export interface MovementResult {
  journalReference: string;
  externalId: string;
  amount: number;
  newBalance: number;
}

export interface TransferResult {
  reference: string;
  sourceAccountId: string;
  destinationAccountId: string;
  amount: number;
  sourceBalance: number;
  destinationBalance: number;
}
