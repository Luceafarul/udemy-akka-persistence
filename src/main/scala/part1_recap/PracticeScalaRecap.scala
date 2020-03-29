package part1_recap

object PracticeScalaRecap extends App {
  // Implicits
  implicit val timeout = 3000
  def setTimeout(f: () => Unit)(implicit timeout: Int): Unit = f()

  setTimeout(() => println("Timeout")) // Second argument list injected by compiler

  // Conversation
  // 1. Implicit methods
  final case class Person(name: String) {
    def greet: String = s"Hi, my name is $name"
  }
  implicit def fromStringToPerson(name: String): Person = Person(name)
  val greeting = "Peter".greet
  println(greeting)
  // Under the hood compiler call fromStringToPerson("Peter").greet

  // 2. Implicit classes
  implicit class Dog(name: String) {
    def bark: Unit = println("Bark-bark!")
  }
  "Lessie".bark
  // Under the hood: new Dog("Lessie").bark

  // Implicit organization
  // 1. Local scope
  implicit val numberOrdering: Ordering[Int] = Ordering.fromLessThan(_ > _)
  val sorted = List(1, 2, 3, 4, 5).sorted // (numberOrdering) pass as implicit parameter to sorted method
  println(sorted)

  // 2. Imported scope (import execution context)

  // 3. Companion objects of the types involved in the call
  object Person {
    implicit val personOrdering: Ordering[Person] = Ordering.fromLessThan((f, s) => f.name.compareTo(s.name) > 0)
  }

  val persons = List(Person("Bob"), Person("Annie"), Person("Drake"))
  val sortedPersons = persons.sorted // persons.sorted(Person.personOrdering)
  println(sortedPersons)
}
