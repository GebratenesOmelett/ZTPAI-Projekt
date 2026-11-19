-- Bezpieczne ponawianie importów (retry z RabbitMQ):
-- run_token identyfikuje bieżącą próbę workera; batche z wcześniejszych prób są ignorowane.
-- failed_batches zlicza batche, które po wyczerpaniu retry trafiły do DLQ — taki import
-- nie jest uznawany za udany (nie liczy się do reguły "3 kolejnych udanych importów").
ALTER TABLE import_job
    ADD COLUMN run_token      VARCHAR(36),
    ADD COLUMN attempts       INT NOT NULL DEFAULT 0,
    ADD COLUMN failed_batches INT NOT NULL DEFAULT 0;
