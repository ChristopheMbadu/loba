CREATE TABLE IF NOT EXISTS studio_admins (
  id UUID PRIMARY KEY,
  email TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS studio_sessions (
  token_hash TEXT PRIMARY KEY,
  admin_id UUID NOT NULL REFERENCES studio_admins(id) ON DELETE CASCADE,
  expires_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE IF NOT EXISTS studio_login_attempts (
  key TEXT PRIMARY KEY,
  attempts INTEGER NOT NULL DEFAULT 1,
  resets_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE IF NOT EXISTS studio_lessons (
  id TEXT PRIMARY KEY,
  content JSONB NOT NULL,
  published JSONB,
  version INTEGER NOT NULL DEFAULT 1,
  published_version INTEGER,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at TIMESTAMPTZ
);
CREATE TABLE IF NOT EXISTS studio_activity (
  id BIGSERIAL PRIMARY KEY,
  admin_email TEXT NOT NULL,
  action TEXT NOT NULL,
  lesson_title TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS studio_migrations (
  name TEXT PRIMARY KEY,
  applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS studio_audio (
  id UUID PRIMARY KEY,
  mime TEXT NOT NULL,
  data BYTEA NOT NULL,
  admin_email TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS studio_sessions_expiry ON studio_sessions(expires_at);
ALTER TABLE studio_admins ADD COLUMN IF NOT EXISTS approved BOOLEAN NOT NULL DEFAULT TRUE;

-- Learning languages are managed in Studio. Learners only see visible ones.
CREATE TABLE IF NOT EXISTS studio_languages (
  code TEXT PRIMARY KEY,
  name_fr TEXT NOT NULL,
  name_en TEXT NOT NULL,
  greeting TEXT NOT NULL DEFAULT '',
  cheer TEXT NOT NULL DEFAULT '',
  region_fr TEXT NOT NULL DEFAULT '',
  region_en TEXT NOT NULL DEFAULT '',
  color TEXT NOT NULL DEFAULT '#1c9bef',
  position INTEGER NOT NULL DEFAULT 100,
  published BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Learner accounts: name, email and a password hash. Separate from Studio editors.
CREATE TABLE IF NOT EXISTS learners (
  id UUID PRIMARY KEY,
  email TEXT NOT NULL UNIQUE,
  name TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS learner_sessions (
  token_hash TEXT PRIMARY KEY,
  learner_id UUID NOT NULL REFERENCES learners(id) ON DELETE CASCADE,
  expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS learner_sessions_expiry ON learner_sessions(expires_at);
CREATE TABLE IF NOT EXISTS learner_progress (
  learner_id UUID PRIMARY KEY REFERENCES learners(id) ON DELETE CASCADE,
  state JSONB NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
