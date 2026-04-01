export interface Client {
  id?: number;
  name: string;
  email: string;
  appPassword?: string;
  phone?: string;
  documentName?: string;
  createdAt?: string;
}

export interface Lead {
  id?: number;
  email: string;
  allEmails?: string;
  institutionName?: string;
  city?: string;
  phone?: string;
  address?: string;
  latitude?: number;
  longitude?: number;
  website?: string;
  source?: string;
  status?: 'NEW' | 'CONTACTED' | 'INTERESTED' | 'INVALID';
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
  leadInstitutionName?: string;
  leadCity?: string;
  status: CampaignSendStatus;
  sentAt?: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface CampaignStats {
  total: number;
  sent: number;
  pending: number;
  failed: number;
}

export interface CampaignLog {
  id: string;
  timestamp: Date;
  level: 'info' | 'success' | 'warning' | 'error';
  message: string;
  details?: string;
}
