# Sistema de Entregas — Samuel Frutas

Aplicativo Android independente para operação de entregas.

## Arquitetura inicial

- APK Android
- SQLite local como armazenamento operacional
- Firebase/Firestore como banco central (integração em etapa posterior)
- Dashboard Web responsivo e mobile-first (etapa posterior)

## Regras de infraestrutura

Este projeto não cria nem depende de um novo servidor Render permanente.

## Regra offline

Os dados operacionais são gravados primeiro no SQLite. Um registro local não deve ser removido antes de existir confirmação de sincronização no banco central.

## Fluxo do projeto

1. Fundação Android
2. Telas do entregador
3. Regras de pagamento
4. Fluxo de entregas
5. Offline + SQLite
6. Firebase/Firestore
7. Dashboard
8. Cobranças
9. Google Contatos
10. Taxa do entregador
11. Fechamento
12. Histórico
13. Segurança
14. Testes completos
15. Versão final
