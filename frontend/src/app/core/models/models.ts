export interface Client {
  id?: number;
  name: string;
  email: string;
  appPassword?: string;
  phone?: string;
  categories?: ClientCategoryDTO[];
  createdAt?: string;
}

export interface Category {
  id?: number;
  name: string;
  color?: string;
  active?: boolean;
  keywordCount?: number;
  createdAt?: string;
}

export interface Keyword {
  id?: number;
  nameEn: string;
  nameDe: string;
  categoryId?: number;
  categoryName?: string;
  active?: boolean;
}

export interface CategoryWithKeywords {
  id?: number;
  name: string;
  color?: string;
  active?: boolean;
  keywords: Keyword[];
}

export interface ClientCategoryDTO {
  categoryId: number;
  categoryName: string;
  color?: string;
  hasDocument: boolean;
}

export interface ClientCategoryDocument {
  id?: number;
  clientId: number;
  categoryId: number;
  documentName?: string;
  documentContentType?: string;
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
  categoryIds?: number[];
  categoryNames?: string[];
  status?: 'NEW' | 'CONTACTED' | 'INTERESTED' | 'INVALID';
  createdAt?: string;
}

export enum CampaignStatus {
  DRAFT = 'DRAFT',
  RUNNING = 'RUNNING',
  STOP_REQUESTED = 'STOP_REQUESTED',
  COMPLETED = 'COMPLETED'
}

export interface Campaign {
  id?: number;
  name: string;
  status: CampaignStatus;
  clientId: number;
  clientName?: string;
  scheduledAt?: string;
  createdAt?: string;
}

export enum CampaignSendStatus {
  PENDING = 'PENDING',
  SENT = 'SENT',
  FAILED = 'FAILED',
  REPLIED = 'REPLIED',
  BOUNCED = 'BOUNCED'
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
  repliedAt?: string;
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
  replied: number;
  pending: number;
  failed: number;
  bounced: number;
}

export interface CampaignLog {
  id: string;
  timestamp: Date;
  level: 'info' | 'success' | 'warning' | 'error';
  message: string;
  details?: string;
}

export interface GmailScanResult {
  scannedCount: number;
  markedAsSentCount: number;
  markedAsRepliedCount?: number;
}

export interface CampaignScheduleRequest {
  scheduledAt: string;
  subject: string;
  body: string;
  delaySeconds: number;
  htmlBody: boolean;
}

export interface BulkLeadImportResponse {
  saved: Lead[];
  errors: string[];
}

export interface BulkImportResult {
  imported: number;
  skipped: number;
  failed: number;
  errors: string[];
}

export interface SelectiveSendRequest {
  sendIds: number[];
  subject: string;
  body: string;
  delaySeconds: number;
  htmlBody: boolean;
}

export interface EmailAuditItem {
  id: number;
  leadId: number;
  institutionName?: string;
  email: string;
  primary: boolean;
  issueType: 'INVALID' | 'DUPLICATE';
  duplicateCount: number;
}

export interface DeleteLeadEmailsResponse {
  deletedCount: number;
  skippedCount: number;
  skippedEmailIds: number[];
}

export interface PlaceDistrictTree {
  id: number;
  name: string;
}

export interface PlaceCityTree {
  id: number;
  name: string;
  districts: PlaceDistrictTree[];
}

export interface PlaceStateTree {
  id: number;
  name: string;
  cities: PlaceCityTree[];
}

export interface PlaceCountryTree {
  id: number;
  code: string;
  name: string;
  states: PlaceStateTree[];
}

export type SearchCombinationStatus = 'PENDING' | 'LAUNCHED' | 'FAILED';

export interface SearchCombination {
  id: number;
  keywordId: number;
  keywordNameEn: string;
  keywordNameDe: string;
  categoryId: number;
  categoryName: string;
  cityId?: number;
  cityName?: string;
  districtId?: number;
  districtName?: string;
  placeDisplayName: string;
  status: SearchCombinationStatus;
  maxResults: number;
  launchedAt?: string;
  failedAt?: string;
  failureReason?: string;
  createdAt?: string;
}

export interface GenerateSearchCombinationsRequest {
  keywordIds: number[];
  stateIds: number[];
  cityIds: number[];
  districtIds: number[];
  maxResults: number;
}

export interface GenerateSearchCombinationsResponse {
  created: number;
  existing: number;
}

export interface LaunchSearchCombinationRequest {
  status: SearchCombinationStatus;
  failureReason?: string;
  maxResults?: number;
}

export type ScrapeStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';

export interface ScrapeProgress {
  jobId: string;
  status: ScrapeStatus;
  message: string;
  leadsFound?: number;
  leadsImported?: number;
  errorMessage?: string;
  keywords?: string;
  cities?: string;
  startedAt?: string;
  finishedAt?: string;
}
