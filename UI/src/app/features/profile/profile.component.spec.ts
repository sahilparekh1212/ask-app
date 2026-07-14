import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { ProfileComponent } from './profile.component';
import { environment } from '../../../environments/environment';

describe('ProfileComponent', () => {
  let component: ProfileComponent;
  let fixture: ComponentFixture<ProfileComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProfileComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(ProfileComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should create and load the profile on init', () => {
    fixture.detectChanges(); // triggers ngOnInit → GET /auth/me
    const req = httpMock.expectOne(`${environment.authApiUrl}/auth/me`);
    req.flush({ userId: 'demo-user', email: 'demo@askapp.dev', name: 'Demo User' });

    expect(component).toBeTruthy();
    expect(component.profile()?.userId).toBe('demo-user');
  });
});
