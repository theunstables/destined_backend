-- LoveOS Daily Question reciprocity loop (PostgreSQL)

CREATE TABLE daily_question_catalog (
    question_key varchar(60)   PRIMARY KEY,
    position     integer       NOT NULL UNIQUE,
    prompt       varchar(500)  NOT NULL,
    active       boolean       NOT NULL DEFAULT true,
    created_at   timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT ck_daily_question_position CHECK (position >= 0),
    CONSTRAINT ck_daily_question_prompt CHECK (length(btrim(prompt)) > 0)
);

-- Starter content is deliberately low-risk and non-clinical. Product/content review is
-- required before production; old assignments retain their prompt snapshot if wording changes.
INSERT INTO daily_question_catalog (question_key, position, prompt) VALUES
  ('small-joy',       0, 'What small thing made you smile today?'),
  ('appreciation',    1, 'What is one thing you appreciated about us recently?'),
  ('repeat-day',      2, 'Which day together would you happily repeat?'),
  ('learn-together',  3, 'What would you enjoy learning together?'),
  ('comfort',         4, 'What makes an ordinary day together feel special?'),
  ('shared-song',     5, 'Which song feels connected to a memory of us?'),
  ('next-adventure',  6, 'What simple adventure should we plan next?'),
  ('kind-moment',     7, 'What kind moment from this week do you want to remember?'),
  ('favorite-routine',8, 'Which of our little routines is your favorite?'),
  ('place-return',    9, 'Where we have been together would you most like to revisit?'),
  ('future-photo',   10, 'What future moment of us would you love to photograph?'),
  ('today-highlight',11, 'What was the best part of your day?');

CREATE TABLE daily_question_days (
    id               uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    couple_id        uuid         NOT NULL REFERENCES couples (id) ON DELETE CASCADE,
    question_key     varchar(60)  NOT NULL REFERENCES daily_question_catalog (question_key),
    question_date    date         NOT NULL,
    prompt_snapshot  varchar(500) NOT NULL,
    memory_id        uuid         REFERENCES memories (id) ON DELETE SET NULL,
    completed_at     timestamptz,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_daily_question_couple_date UNIQUE (couple_id, question_date)
);

CREATE INDEX idx_daily_question_days_couple_date
    ON daily_question_days (couple_id, question_date DESC);

CREATE TABLE daily_question_answers (
    id          uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    day_id      uuid          NOT NULL REFERENCES daily_question_days (id) ON DELETE CASCADE,
    user_id     uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    answer      varchar(1500) NOT NULL,
    answered_at timestamptz   NOT NULL DEFAULT now(),
    updated_at  timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT uq_daily_question_answer UNIQUE (day_id, user_id),
    CONSTRAINT ck_daily_question_answer CHECK (length(btrim(answer)) > 0)
);

CREATE INDEX idx_daily_question_answers_day ON daily_question_answers (day_id);
