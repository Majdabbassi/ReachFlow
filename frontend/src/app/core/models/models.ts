export interface Client {
  id?: number;
  name: string;
  email: string;
  appPassword?: string;
  phone?: string;
  createdAt?: string;
}

export interface Lead {
  id?: number;
  email: string;
  institutionName?: string;
  city?: string;
  address?: string;
  latitude?: number;
  longitude?: number;
  website?: string;
  source?: string;
  createdAt?: string;
}

export enum CampaignStatus {
  DRAFT = 'DRAFT',
  RUNNING = 'RUNNING',
  COMPLETED = 'COMPLETED'
}

export interface Campaign {
  id?: number;
  name: string;
  status: CampaignStatus;
  clientId: number;
  clientName?: string;
  createdAt?: string;
}

export enum CampaignSendStatus {
  PENDING = 'PENDING',
  SENT = 'SENT',
  FAILED = 'FAILED'
}

export interface CampaignSend {
  id?: number;
  campaignId: number;
  leadId: number;
  leadEmail: string;
  status: CampaignSendStatus;
  sentAt?: string;
}

export interface CampaignStats {
  total: number;
  sent: number;
  pending: number;
  failed: number;
}
