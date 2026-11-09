CREATE TABLE app_user (
 id UUID PRIMARY KEY, username VARCHAR(80) NOT NULL UNIQUE, display_name VARCHAR(120) NOT NULL,
 password_hash VARCHAR(100) NOT NULL, role VARCHAR(20) NOT NULL, specialty VARCHAR(200) NOT NULL DEFAULT '',
 enabled BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE lead (
 id UUID PRIMARY KEY, source_key VARCHAR(200) NOT NULL UNIQUE, name VARCHAR(240) NOT NULL,
 category VARCHAR(80) NOT NULL, city VARCHAR(150), address VARCHAR(500), latitude DOUBLE PRECISION, longitude DOUBLE PRECISION,
 website VARCHAR(1500), phone VARCHAR(100), whatsapp VARCHAR(100), contact_name VARCHAR(150),
 source_url VARCHAR(1500) NOT NULL, source_name VARCHAR(150) NOT NULL, source_updated_at TIMESTAMP WITH TIME ZONE,
 fetched_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
 state VARCHAR(40) NOT NULL DEFAULT 'AVAILABLE', owner_id UUID REFERENCES app_user(id),
 reserved_until TIMESTAMP WITH TIME ZONE, follow_up TIMESTAMP WITH TIME ZONE,
 score INTEGER NOT NULL DEFAULT 0, analysis_state VARCHAR(40) NOT NULL DEFAULT 'PENDING',
 analysis_reasons TEXT NOT NULL DEFAULT '[]', analysis_details TEXT NOT NULL DEFAULT '{}',
 analyzed_at TIMESTAMP WITH TIME ZONE, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX lead_geo_idx ON lead(latitude,longitude);
CREATE INDEX lead_owner_idx ON lead(owner_id,state);
CREATE TABLE audit_event (
 id UUID PRIMARY KEY, lead_id UUID REFERENCES lead(id), actor_id UUID REFERENCES app_user(id),
 action VARCHAR(60) NOT NULL, detail VARCHAR(2000), created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX audit_lead_idx ON audit_event(lead_id,created_at);
CREATE TABLE feedback (
 id UUID PRIMARY KEY, lead_id UUID NOT NULL REFERENCES lead(id), actor_id UUID NOT NULL REFERENCES app_user(id),
 state VARCHAR(40) NOT NULL, comment VARCHAR(2000) NOT NULL, follow_up TIMESTAMP WITH TIME ZONE,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE work_job (
 id UUID PRIMARY KEY, kind VARCHAR(40) NOT NULL, status VARCHAR(40) NOT NULL, actor_id UUID REFERENCES app_user(id),
 lead_id UUID REFERENCES lead(id), cache_key VARCHAR(300), payload TEXT NOT NULL,
 result TEXT, error VARCHAR(2000), attempts INTEGER NOT NULL DEFAULT 0,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX job_pending_idx ON work_job(status,created_at);
CREATE INDEX job_cache_idx ON work_job(cache_key,created_at);
