using System;
using System.Collections.Generic;
using System.Drawing;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Text.Json;
using DeskMadeline;

static class ReferenceTrace {
    const float Dt = 1f / 60;
    static float Private(Player p, string name) => (float)typeof(Player).GetField(name, BindingFlags.Instance | BindingFlags.NonPublic).GetValue(p);
    static object State(Player p) => new {
        x = p.Pos.X, y = -p.Pos.Y, vx = p.Speed.X, vy = -p.Speed.Y, stamina = p.Stamina,
        dashes = p.Dashes, motion = p.State, ducking = p.Ducking, grounded = p.onGround,
        coyote = Private(p, "jumpGraceTimer"), variableJump = Private(p, "varJumpTimer"),
        dashTime = Private(p, "dashTime"), dashAttack = Private(p, "dashAttackTimer"),
        refillCooldown = Private(p, "dashRefillCooldownTimer"), wallBoost = Private(p, "wallBoostTimer"),
        wallRetention = Private(p, "wallSpeedRetentionTimer")
    };
    // X, Y(up), jump held, jump edge, dash edge, grab held, crouch dash edge.
    static int[] Input(int x = 0, int y = 0, int held = 0, int jump = 0, int dash = 0, int grab = 0, int crouch = 0)
        => new[] { x, y, held, jump, dash, grab, crouch };
    static object Run(string name, float[] initial, float[][] boxes, List<int[]> inputs,
        float stamina = 110, int initialState = 0, bool ducking = false) {
        var p = new Player { Ghost = true, MinX = -100000, MaxX = 100000,
            Pos = new PointF(initial[0], -initial[1]), Speed = new PointF(initial[2], -initial[3]),
            Stamina = stamina, State = initialState, Ducking = ducking,
            Solids = boxes.Select((b, i) => new Solid { Id = new IntPtr(i + 1), L = b[0], T = -b[3], R = b[2], B = -b[1] }).ToList() };
        var frames = new List<object>();
        foreach (int[] raw in inputs) {
            if (raw[3] != 0) p.BufferJump();
            if (raw[4] != 0) p.BufferDash();
            if (raw[6] != 0) p.BufferDash(true);
            var input = new PetInput { MoveX = raw[0], MoveY = -raw[1], AimX = raw[0], AimY = -raw[1],
                JumpHeld = raw[2] != 0, JumpPressed = p.HasJumpBuffer, GrabHeld = raw[5] != 0 };
            p.Update(Dt, input);
            frames.Add(new { input = raw, state = State(p) });
        }
        return new { name, initial, initialStamina = stamina, initialState, initialDucking = ducking, boxes, frames };
    }
    static int Main(string[] args) {
        CultureInfo.CurrentCulture = CultureInfo.InvariantCulture;
        var scenarios = new List<object>();
        float[][] floor = { new float[]{-10000,-40,10000,0} };
        for (int technique = 0; technique < 7; technique++) {
            var inputs = new List<int[]>();
            for (int frame = 0; frame < 120; frame++) {
                int x = technique == 6 && frame >= 14 ? -1 : 1;
                int y = technique == 1 || technique == 4 ? -1 : 0;
                int jump = frame == (technique == 0 ? 0 : technique == 2 ? 8 : 14) ? 1 : 0;
                inputs.Add(Input(x, frame < 6 ? y : 0, frame < 40 ? 1 : 0, jump, frame == 2 ? 1 : 0,
                    crouch: technique == 3 && frame == 2 ? 1 : 0));
            }
            scenarios.Add(Run("ground-tech-" + technique, new float[]{0,0,0,0}, floor, inputs));
        }
        var ultra = new List<int[]>();
        for (int i=0;i<100;i++) ultra.Add(Input(1,-1, dash:i==0?1:0));
        scenarios.Add(Run("ultra", new float[]{0,40,400,0}, floor, ultra));
        float[][] wall = {new float[]{7,-40,30,200}, new float[]{-1000,-40,1000,0}};
        var climb = new List<int[]>();
        for (int i=0;i<220;i++) climb.Add(Input(i%32<24?1:-1,1,1,i%32==0?1:0,grab:1));
        scenarios.Add(Run("climb-wallboost",new float[]{3,20,0,0},wall,climb));
        var coffee = new List<int[]>();
        for (int i=0;i<25;i++) coffee.Add(Input(0,0,1,i==0 || i==1?1:0));
        scenarios.Add(Run("two-key-wall-jump-window",new float[]{3,20,0,0},wall,coffee));
        var halfStamina = new List<int[]>();
        for (int i=0;i<25;i++) halfStamina.Add(Input(i==0?0:-1,0,1,i==0?1:0,grab:1));
        scenarios.Add(Run("half-stamina-wallboost",new float[]{3,20,0,0},wall,halfStamina,55,Player.StClimb));
        float[][] lowWall = {new float[]{7,-100,30,100},new float[]{-30,26,7,30}};
        var crouchWall = new List<int[]>();
        for(int i=0;i<25;i++) crouchWall.Add(Input(0,0,1,i==0?1:0,grab:1));
        scenarios.Add(Run("duck-climb-low-ceiling",new float[]{3,20,0,0},lowWall,crouchWall,60,Player.StClimb,true));
        var bunny = new List<int[]>();
        for(int i=0;i<70;i++) bunny.Add(Input(1,0,i<30?1:0,i==1 || i==16?1:0));
        scenarios.Add(Run("bunny-hop-speed",new float[]{0,1,325,-160},floor,bunny));
        float[][] room = {new float[]{-200,-40,200,0},new float[]{-200,0,-180,160},new float[]{180,0,200,160},
            new float[]{-110,0,-40,3},new float[]{20,0,50,35},new float[]{65,45,140,54},new float[]{-150,100,170,108}};
        for(int seed=0;seed<4;seed++) {
            var random=new Random(seed+7481); var inputs=new List<int[]>(); int x=1,y=0,grab=0;
            for(int i=0;i<400;i++) {
                if(i%7==0){x=random.Next(-1,2);y=random.Next(-1,2);grab=random.Next(2);}
                inputs.Add(Input(x,y,random.Next(3)>0?1:0,random.Next(12)==0?1:0,random.Next(17)==0?1:0,grab,random.Next(51)==0?1:0));
            }
            scenarios.Add(Run("room-"+seed,new float[]{0,0,0,0},room,inputs));
        }
        File.WriteAllText(args[0],JsonSerializer.Serialize(new{commit="8404a89e2a90cbafe35a97ef40f640083afd3c4c",scenarios}));
        Console.WriteLine("Upstream Player.cs: " + scenarios.Count + " scenarios recorded without desktop/game startup.");
        return 0;
    }
}
