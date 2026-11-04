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
