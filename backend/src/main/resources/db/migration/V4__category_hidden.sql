
-- 카테고리 숨김(사이드바에서 감춘다). 기존 행은 FALSE(보임)로 채워진다
ALTER TABLE categories ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE;
