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
  primaryEmail?: string;
  emails: string[];
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
  leadEmailId: number;
  email: string;
  leadId: number;
  leadInstitutionName?: string;
  leadCity?: string;
  status: CampaignSendStatus;
  sentAt?: string;
}

export interface ArchivedClient {
  id: number;
  name: string;
  email: string;
  appPassword: string;
  phone?: string;
  documentName?: string;
  documentContentType?: string;
  createdAt?: string;
  archivedAt: string;
  originalId: number;
}

export interface ArchivedCampaign {
  id: number;
  name: string;
  status: CampaignStatus;
  createdAt?: string;
  archivedClientId: number;
  originalId: number;
  stats: CampaignStats;
}

export interface ArchivedCampaignSend {
  email: string;
  leadInstitutionName?: string;
  leadCity?: string;
  status: CampaignSendStatus;
  sentAt?: string;
  archivedCampaignId: number;
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
