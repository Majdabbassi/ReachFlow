import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

export interface EmailTemplate {
  id: string;
  name: string;
  subject: string;
  body: string;
  delaySeconds?: number;
  htmlBody?: boolean;
  createdAt?: Date;
}

@Injectable({
  providedIn: 'root'
})
export class EmailTemplateService {
  private readonly STORAGE_KEY = 'email_templates';
  private templatesSubject = new BehaviorSubject<EmailTemplate[]>(this.loadTemplates());
  public templates$ = this.templatesSubject.asObservable();

  // Default Chayma Bahri template
  private defaultChaymaTemplate: EmailTemplate = {
    id: 'chaymabahri',
    name: 'Chayma Bahri - Nursing Apprenticeship',
    subject: 'Bewerbung um einen Ausbildungsplatz als Pflegefachfrau ab Oktober',
    body: `<div style="font-family: Arial, sans-serif; color: #222; max-width: 600px; line-height: 1.5; font-size: 14px;">
  <p style="margin: 0 0 10px 0;">Sehr geehrte Damen und Herren,</p>
  <p style="margin: 0 0 10px 0;">
    mein Name ist <strong>Chayma Bahri</strong> und ich bewerbe mich um eine Ausbildung 
    zum Pflegefachmann in Ihrer Einrichtung.
  </p>
  <p style="margin: 0 0 10px 0;">
    Ich arbeite gerne mit Menschen zusammen und bringe Verantwortungsbewusstsein, 
    Geduld sowie Teamfähigkeit mit. Durch meine Erfahrungen im Bereich Sport und 
    Gesundheit habe ich gelernt, aufmerksam zu sein und andere professionell zu unterstützen.
  </p>
  <p style="margin: 0 0 10px 0;">
    Ich möchte mich im Pflegebereich weiterentwickeln und freue mich sehr auf die Möglichkeit, 
    in Ihrem Team mitzuwirken.
  </p>
  <p style="margin: 18px 0 5px 0;">Mit freundlichen Grüßen</p>
  <p style="margin: 0;"><strong>Chayma Bahri</strong></p>
</div>`,
    delaySeconds: 60,
    htmlBody: true,
    createdAt: new Date()
  };

  // Default Eya Bouhali template
  private defaultEyaTemplate: EmailTemplate = {
    id: 'eyabouhali',
    name: 'Eya Bouhali - Nursing Apprenticeship',
    subject: 'Bewerbung um einen Ausbildungsplatz als Pflegefachfrau ab April 2027',
    body: `<div style="font-family: Arial, sans-serif; color: #222; max-width: 600px; line-height: 1.5; font-size: 14px;">
  <p style="margin: 0 0 10px 0;">Sehr geehrte Damen und Herren,</p>
  <p style="margin: 0 0 10px 0;">
    mein Name ist <strong>EYA BOUHALI</strong> und ich bewerbe mich um eine Ausbildung 
    zum Pflegefachmann in Ihrer Einrichtung.
  </p>
  <p style="margin: 0 0 10px 0;">
    Ich arbeite gerne mit Menschen zusammen und bringe Verantwortungsbewusstsein, 
    Geduld sowie Teamfähigkeit mit. Durch meine Erfahrungen im Bereich Sport und 
    Gesundheit habe ich gelernt, aufmerksam zu sein und andere professionell zu unterstützen.
  </p>
  <p style="margin: 0 0 10px 0;">
    Ich möchte mich im Pflegebereich weiterentwickeln und freue mich sehr auf die Möglichkeit, 
    in Ihrem Team mitzuwirken.
  </p>
  <p style="margin: 18px 0 5px 0;">Mit freundlichen Grüßen</p>
  <p style="margin: 0;"><strong>EYA BOUHALI</strong></p>
</div>`,
    delaySeconds: 60,
    htmlBody: true,
    createdAt: new Date()
  };

  constructor() {
    // Ensure default templates exist
    this.ensureDefaultTemplates();
  }

  private loadTemplates(): EmailTemplate[] {
    try {
      const stored = localStorage.getItem(this.STORAGE_KEY);
      return stored ? JSON.parse(stored) : [];
    } catch {
      return [];
    }
  }

  private saveToStorage(templates: EmailTemplate[]): void {
    localStorage.setItem(this.STORAGE_KEY, JSON.stringify(templates));
    this.templatesSubject.next(templates);
  }

  private ensureDefaultTemplates(): void {
    const templates = this.loadTemplates();
    let updated = false;
    
    if (!templates.find(t => t.id === 'chaymabahri')) {
      templates.push(this.defaultChaymaTemplate);
      updated = true;
    }
    
    if (!templates.find(t => t.id === 'eyabouhali')) {
      templates.push(this.defaultEyaTemplate);
      updated = true;
    }
    
    if (updated) {
      this.saveToStorage(templates);
    }
  }

  getTemplates(): Observable<EmailTemplate[]> {
    return this.templates$;
  }

  getTemplateById(id: string): EmailTemplate | undefined {
    return this.templatesSubject.value.find(t => t.id === id);
  }

  saveTemplate(template: Omit<EmailTemplate, 'id' | 'createdAt'>): EmailTemplate {
    const templates = this.templatesSubject.value;
    const newTemplate: EmailTemplate = {
      ...template,
      id: `template-${Date.now()}`,
      createdAt: new Date()
    };
    templates.push(newTemplate);
    this.saveToStorage(templates);
    return newTemplate;
  }

  updateTemplate(id: string, updates: Partial<EmailTemplate>): void {
    const templates = this.templatesSubject.value;
    const index = templates.findIndex(t => t.id === id);
    if (index >= 0) {
      templates[index] = { ...templates[index], ...updates };
      this.saveToStorage(templates);
    }
  }

  deleteTemplate(id: string): void {
    const templates = this.templatesSubject.value.filter(t => t.id !== id);
    this.saveToStorage(templates);
  }
}
