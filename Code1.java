package ProblemeDesPhilosophes;

import java.util.concurrent.Semaphore;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class Philosophe extends Thread {
    private int id;
    private Semaphore leftFork;
    private Semaphore rightFork;
    private Semaphore arbitre; // Sémaphore global pour éviter la famine
    private AtomicInteger eatCount; // Compteur de repas pour ce philosophe
    private PriorityBlockingQueue<Philosophe> priorityQueue; // File de priorité
    private Lock forkLock; // Verrou pour la prise atomique des fourchettes

    public Philosophe(int id, Semaphore leftFork, Semaphore rightFork, Semaphore arbitre, AtomicInteger eatCount, PriorityBlockingQueue<Philosophe> priorityQueue, Lock forkLock) {
        this.id = id;
        this.leftFork = leftFork;
        this.rightFork = rightFork;
        this.arbitre = arbitre;
        this.eatCount = eatCount;
        this.priorityQueue = priorityQueue;
        this.forkLock = forkLock;
    }

    public int getEatCount() {
        return eatCount.get();
    }

    public int getIdPhilosophe() {
        return id;
    }

    private void think() throws InterruptedException {
        System.out.println("Philosophe " + id + " est en train de penser.");
        Thread.sleep((long) (Math.random() * 1000));
    }

    private void eat() throws InterruptedException {
        System.out.println("Philosophe " + id + " est en train de manger.");
        Thread.sleep((long) (Math.random() * 1000));
        eatCount.incrementAndGet(); // Incrémenter le compteur de repas
    }

    @Override
    public void run() {
        try {
            while (true) {
                think();

                // S'inscrire dans la file de priorité
                priorityQueue.add(this);

                // Attendre que l'arbitre donne la permission
                arbitre.acquire();

                // Prendre les fourchettes de manière atomique
                forkLock.lock();
                try {
                    leftFork.acquire();
                    System.out.println("Philosophe " + id + " a pris la fourchette gauche.");
                    rightFork.acquire();
                    System.out.println("Philosophe " + id + " a pris la fourchette droite.");
                } finally {
                    forkLock.unlock();
                }

                eat();

                // Relâcher les fourchettes
                rightFork.release();
                System.out.println("Philosophe " + id + " a relâché la fourchette droite.");
                leftFork.release();
                System.out.println("Philosophe " + id + " a relâché la fourchette gauche.");

                // Libérer la permission
                arbitre.release();

                // Se retirer de la file de priorité
                priorityQueue.remove(this);
            }
        } catch (InterruptedException e) {
            System.out.println("Philosophe " + id + " a été interrompu.");
            Thread.currentThread().interrupt();
        } finally {
            // S'assurer que les fourchettes sont relâchées en cas d'erreur
            if (leftFork.availablePermits() == 0) leftFork.release();
            if (rightFork.availablePermits() == 0) rightFork.release();
        }
    }
}

class DinerPhilosophes {
    public static void main(String[] args) {
        int numPhilosophes = 5;
        Semaphore[] forks = new Semaphore[numPhilosophes];
        Philosophe[] philosophes = new Philosophe[numPhilosophes];
        AtomicInteger[] eatCount = new AtomicInteger[numPhilosophes];

        // Initialiser les sémaphores (fourchettes) et les compteurs de repas
        for (int i = 0; i < numPhilosophes; i++) {
            forks[i] = new Semaphore(1);
            eatCount[i] = new AtomicInteger(0);
        }

        // Sémaphore global pour éviter la famine
        Semaphore arbitre = new Semaphore(numPhilosophes - 1);

        // File de priorité pour gérer l'ordre des philosophes
        PriorityBlockingQueue<Philosophe> priorityQueue = new PriorityBlockingQueue<>(numPhilosophes, (p1, p2) -> {
            return Integer.compare(p1.getEatCount(), p2.getEatCount()); // Priorité au philosophe qui a mangé le moins
        });

        // Verrou pour la prise atomique des fourchettes
        Lock forkLock = new ReentrantLock();

        // Créer et démarrer les philosophes
        for (int i = 0; i < numPhilosophes; i++) {
            Semaphore leftFork = forks[i];
            Semaphore rightFork = forks[(i + 1) % numPhilosophes];
            philosophes[i] = new Philosophe(i, leftFork, rightFork, arbitre, eatCount[i], priorityQueue, forkLock);
            philosophes[i].start();
        }

        // Thread pour gérer l'arbitre en fonction de la file de priorité
        new Thread(() -> {
            while (true) {
                try {
                    // Attendre qu'un philosophe demande la permission
                    Philosophe nextPhilosophe = priorityQueue.take(); // Prend le philosophe qui a mangé le moins
                    arbitre.acquire(); // Donne la permission à ce philosophe

                    // Simuler un délai pour la prise de décision de l'arbitre
                    Thread.sleep(100);

                    // Libérer la permission pour le philosophe suivant
                    arbitre.release();
                } catch (InterruptedException e) {
                    System.out.println("Arbitre a été interrompu.");
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }).start();
    }
}