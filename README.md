# Imperal
A (Alomst) purely functional, dynamic, and imperative programming language?

# How? and What?
The first question that would come to anybody's mind after hearing that a language is both imperative and purely functonal is how would that work? If you understand let constructs in functional programming languages then the following would be easy to follow, but let me just make a simple expression, for those unawarre, that allows two expresssions to be chained together.
```
when (x=2) then (x+8)
```
This expression would result in 10 because in the first part we create a new environment where we set a variable namely x, to be equal to 2, then in the second part we create another environment that's chained to the frst one. Meaning it has access to all of the variables in the former environment. This language Imperal is nothing but this concept taken to another extreme level. Every statement in this language is just
```
when (x=2) then (when(y=3) then (when (y = x+y) then (y)))
```
Did you catch it? We just did what seems like a mutation there, while keeping everything functionally pure. We never altered the state we just made a new one up and starting referencing it instead. This is just the extension of this concept to an extreme end. I am basically stretching the term to a point where it becomes practically meaningless but the experiment continues...

# Why
I don't know either...

# Variables
There are two types of variables, immutable top-level declarations and umm... variables. I mean that the variables you declare on top-level such as the one that follows
```
x = 100
```
cannot be re-assigned as these variables are syntactically immutable,
```
x = 3
```
but on the other hand the normal variables are only semantically immutable and can be assigned and reassigned inside top-level declarations such as what follows
```
main = {
  x = 3
  x = x+1
}
```
Now, this sort of re-assignment is semantically immutable because it just makes up a new context where x is something else and the old x is preserved under the new one.
Variable access doesn't have anything fancy going on it's just like how you'd expect,
```
x
```
This returns x from the nearest context.

# If-else
This is simple, it doesn't nessecarily do anything significantly different from a normal if-else from any language and works as what follows
```
main = if x==3 {
  x = x+1
} else if x<0 {
  x = x+5
} else {
  x = x*x
}
```
Code says it all, what else do you want me to say. There's also a pythonish if-expr and that works as follows,
```
x+5 if x==5 else x*x
```

# While-loops
While loops work on the same concept of making up a new context every time the value of a variable changes, for example
```
main = {
  x = 0
  while x<10 {
    x = x+1
  }
}
```
Here this while loop get's a new x in a different context every time it loops so iteration syntactically works seemlessly with other imperative programming languages.

# Lists
Lists are what you's expect them to be from a standard programming language but with a few quirks. Let's begin with an example,
```
nums = [1, 3, 5, 7]
```
Now, if I decide to add 9 later in the list, I can't really do it with something like push because we want lists to be immutable to keep everything functional so instead we re-assign the list by saying,
```
nums = nums + 9
```

# For loops
Iterating over a list can be done via for loop, such as what follows
```
i = 0
for num in nums{
  i = num+1
}
```
Here for loop creates an inner environment for it's code block to execte in, but bases the environment on the last environment returned by the last iteration. Similarly we have a concept of python-like list comprehensions an they are as what folllows
```
num+1 for num in nums
```
In Python you can also add a if-statement at the end and the same can be done here,
```
num+1 for num in nums -> if num!=5
```
which works as a sort of filter function.

# Functions
Caution: These do something interna that might be considered Impure. More specifically interpreter forward declares them at the beginning of a particular context and they iterally get changed when they are initiaized. It is something that might be considered straight-up impure but who am I to say? Let's continue.
Here we get to referential transparency, remember when I said that when you change a variable, the last form of it is preserved. It meant that the functions of that time have the exact copy of the environment that we provided before changing environment so, they won't return different outputs for the same inputs.

They can be defined as what follows,
```
fun add: a, b {
  return a+b
}
```
or to make it shorter
```
fun add: a, b -> a+b
```
and not to mention that you an have both of these forms as simple variables
```
add = fun: a, b {
  return a+b
}
```
or 
```
add = fun: a, b -> a+b
```
if you prefer.

If we say that
```
x = 5
fun addx: n -> x+n
addx: 5
```
It would return 10 but even if we say,
```
x = 5
fun addx: n -> x+n
x = 6
addx: 5
```
it would still return 10 because of referential transparency.

You can have mutually recursive functions both openly and in lists.
```
ls = [(fun: a, b -> ls/1: a, -b), fun: a, b -> a-b]
```
or openly like,
```
fun inc: n -> dec: n
fun dec: n -> inc: n
```

# Encapsulate
Now, here is an immutable sort of interface that allows you to encapsulate an environment and later use it as a sort of object.
```
env = encap {
  a = 2
  b = 6
  c = 8
}
```
To access, properties of encapsulated environments, you can say
```
env => a
```
You can ofcourse also have factories of encapsulation that simulate a somewhat class-like structure
```
envGen = fun: a, b, c -> encap {
  n = a+b+c
}
```
and create an encapsulation by saying,
```
env = envGen: 3, 6, 9
env => a
env => n
```

## Sencap
Sencap or Shallow Encapsulation is basically encap but with a singe difference, it only captures environment in the given curly braces, it won't catch your creation environment so they are in a  way more object-like then encapsulations. For example if I do,
```
x = 5
env = encap {
  a = "Encapsulation"
}
```
I can later access
```
env => x
```
and get 5 but with Shallow Encapsulation I can do
```
x = 5
env = sencap {
  a = "Shallow Encapsulation"
}
```
and then try to access x
```
env => x
```
then I would get an error.
Making class-like structurs is similar with sencaps, you could just do what follows
```
envGen = fun: a, b, c -> sencap {
  a = a
  b = b
  c = c
  define total = a+b+c
}
```
For every generated Sencap a, b, and c will be precompputed so that you woun't require to access the outside sencap environment as for, define or any other function definition, they wil be computed when objects call them like total isn't a number sitting there, in the example above but it's rather an equation that will be evaluated when called by a sencap for example,
```
env = envGen: 2, 9, 5
env => total!
```
would result in 16.
Thanks for reading through and maybe play around with the language when you're free.
